package dev.ltag.stone_payments.usecases

import android.content.Context
import android.util.Log
import dev.ltag.stone_payments.Result
import br.com.stone.posandroid.providers.PosMifareProvider
import br.com.stone.posandroid.hal.api.mifare.MifareKeyType
import dev.ltag.stone_payments.StonePaymentsPlugin
import stone.application.interfaces.StoneCallbackInterface

class MifareUsecase(
    private val stonePayments: StonePaymentsPlugin,
) {
    private val context = stonePayments.context

    /**
     * Lê dados de um cartão Mifare
     */
    fun readMifareCard(
        block: Int,
        timeout: Int,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        try {
            Log.d("MIFARE_READ_START", "Iniciando leitura do bloco $block")
            
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    Log.d("MIFARE_CARD_DETECTED", "Cartão detectado com sucesso")
                    
                    try {
                        // Ativar o cartão
                        Log.d("MIFARE_ACTIVATE", "Ativando cartão...")
                        mifareProvider.activateCard()
                        Log.d("MIFARE_ACTIVATE", "Cartão ativado")

                        // Calcular o setor
                        val sector = block / 4
                        Log.d("MIFARE_SECTOR", "Bloco $block está no setor $sector")
                        
                        // Lista de chaves para tentar
                        val keysToTry = listOf(
                            // Chave padrão de fábrica
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 
                                       0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                            // Chave alternativa (todos zeros)
                            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                            // Chave MAD (Mifare Application Directory)
                            byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 
                                       0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
                            // Outra chave comum
                            byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 
                                       0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte())
                        )
                        
                        var authenticated = false
                        var usedKey = ""
                        var usedKeyType = ""
                        
                        // Tentar Key A com todas as chaves
                        for ((index, key) in keysToTry.withIndex()) {
                            try {
                                Log.d("MIFARE_AUTH_TRY", "Tentando Key A com chave $index...")
                                mifareProvider.authenticateSector(
                                    MifareKeyType.TypeA,
                                    key,
                                    sector.toByte()
                                )
                                authenticated = true
                                usedKey = "Chave $index"
                                usedKeyType = "Key A"
                                Log.d("MIFARE_AUTH", "Autenticação bem-sucedida com Key A, chave $index")
                                break
                            } catch (e: Exception) {
                                Log.w("MIFARE_AUTH_FAIL", "Key A chave $index falhou: ${e.message}")
                            }
                        }
                        
                        // Se Key A falhou, tentar Key B
                        if (!authenticated) {
                            for ((index, key) in keysToTry.withIndex()) {
                                try {
                                    Log.d("MIFARE_AUTH_TRY", "Tentando Key B com chave $index...")
                                    mifareProvider.authenticateSector(
                                        MifareKeyType.TypeB,
                                        key,
                                        sector.toByte()
                                    )
                                    authenticated = true
                                    usedKey = "Chave $index"
                                    usedKeyType = "Key B"
                                    Log.d("MIFARE_AUTH", "Autenticação bem-sucedida com Key B, chave $index")
                                    break
                                } catch (e: Exception) {
                                    Log.w("MIFARE_AUTH_FAIL", "Key B chave $index falhou: ${e.message}")
                                }
                            }
                        }
                        
                        if (!authenticated) {
                            throw Exception("Não foi possível autenticar o setor $sector. Nenhuma das chaves conhecidas funcionou.")
                        }

                        // Criar buffer para receber os dados
                        val dataBytes = ByteArray(16)
                        
                        Log.d("MIFARE_READ", "Lendo bloco $block...")
                        
                        // Ler o bloco
                        val keyType: Byte = if (usedKeyType == "Key A") 0x60 else 0x61
                        
                        try {
                            mifareProvider.readBlock(block.toByte(), keyType, dataBytes)
                            Log.d("MIFARE_READ", "Bloco lido com sucesso")
                        } catch (readEx: Exception) {
                            Log.e("MIFARE_READ_ERROR", "Erro ao ler bloco", readEx)
                            throw Exception("Erro ao ler bloco $block: ${readEx.message ?: "Erro desconhecido"}")
                        }
                        
                        // Converter para String
                        val dataString = String(dataBytes, Charsets.UTF_8).trim()
                        
                        // Converter para HEX
                        val hexString = StringBuilder()
                        for (b in dataBytes) {
                            hexString.append(String.format("%02X ", b))
                        }

                        mifareProvider.powerOff()
                        Log.d("MIFARE_POWEROFF", "Cartão desligado")

                        Log.d("MIFARE_READ_SUCCESS", "Bloco $block lido: $dataString")
                        Log.d("MIFARE_READ_HEX", "HEX: $hexString")
                        
                        val resultMap = mutableMapOf<String, Any>(
                            "success" to true,
                            "block" to block,
                            "sector" to sector,
                            "data" to dataString,
                            "dataHex" to hexString.toString().trim(),
                            "authMethod" to "$usedKeyType - $usedKey",
                            "message" to "Cartão Mifare lido com sucesso usando $usedKeyType"
                        )
                        
                        callback(Result.Success(resultMap))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                            Log.e("MIFARE_POWEROFF_ERROR", "Erro ao desligar cartão", ignored)
                        }
                        
                        val errorMsg = e.message ?: "Erro desconhecido ao ler cartão Mifare"
                        Log.e("MIFARE_READ_ERROR", errorMsg, e)
                        
                        callback(Result.Error(Exception(errorMsg)))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor e tente novamente.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.message ?: "Unknown error")
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()
            Log.d("MIFARE_EXECUTE", "Aguardando cartão...")

        } catch (e: Exception) {
            val errorMsg = "Exceção ao inicializar leitura Mifare: ${e.message}"
            Log.e("MIFARE_EXCEPTION", errorMsg, e)
            callback(Result.Error(Exception(errorMsg)))
        }
    }

    /**
     * Escreve dados em um cartão Mifare
     */
    fun writeMifareCard(
        data: String,
        block: Int,
        timeout: Int,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        try {
            // Validar o bloco
            if (block % 4 == 3) {
                callback(Result.Error(Exception("Não é possível escrever em blocos de trailer (3, 7, 11, 15, etc.)")))
                return
            }
            
            if (block == 0) {
                callback(Result.Error(Exception("Não é possível escrever no bloco 0 (bloco de fabricante)")))
                return
            }
            
            if (data.length > 16) {
                callback(Result.Error(Exception("Dados devem ter no máximo 16 bytes. Atual: ${data.length} bytes")))
                return
            }

            Log.d("MIFARE_WRITE_START", "Iniciando escrita no bloco $block: '$data'")

            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    Log.d("MIFARE_CARD_DETECTED", "Cartão detectado para escrita")
                    
                    try {
                        // Ativar o cartão
                        Log.d("MIFARE_ACTIVATE", "Ativando cartão...")
                        mifareProvider.activateCard()
                        Log.d("MIFARE_ACTIVATE", "Cartão ativado")

                        // Calcular setor
                        val sector = block / 4
                        Log.d("MIFARE_SECTOR", "Bloco $block está no setor $sector")
                        
                        // Lista de chaves para tentar
                        val keysToTry = listOf(
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 
                                       0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                            byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 
                                       0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
                            byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 
                                       0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte())
                        )
                        
                        var authenticated = false
                        var usedKey = ""
                        var usedKeyType = ""
                        
                        // Tentar Key A
                        for ((index, key) in keysToTry.withIndex()) {
                            try {
                                Log.d("MIFARE_AUTH_TRY", "Tentando Key A com chave $index...")
                                mifareProvider.authenticateSector(
                                    MifareKeyType.TypeA,
                                    key,
                                    sector.toByte()
                                )
                                authenticated = true
                                usedKey = "Chave $index"
                                usedKeyType = "Key A"
                                Log.d("MIFARE_AUTH", "Autenticação bem-sucedida com Key A, chave $index")
                                break
                            } catch (e: Exception) {
                                Log.w("MIFARE_AUTH_FAIL", "Key A chave $index falhou")
                            }
                        }
                        
                        // Tentar Key B se Key A falhou
                        if (!authenticated) {
                            for ((index, key) in keysToTry.withIndex()) {
                                try {
                                    Log.d("MIFARE_AUTH_TRY", "Tentando Key B com chave $index...")
                                    mifareProvider.authenticateSector(
                                        MifareKeyType.TypeB,
                                        key,
                                        sector.toByte()
                                    )
                                    authenticated = true
                                    usedKey = "Chave $index"
                                    usedKeyType = "Key B"
                                    Log.d("MIFARE_AUTH", "Autenticação bem-sucedida com Key B, chave $index")
                                    break
                                } catch (e: Exception) {
                                    Log.w("MIFARE_AUTH_FAIL", "Key B chave $index falhou")
                                }
                            }
                        }
                        
                        if (!authenticated) {
                            throw Exception("Não foi possível autenticar o setor $sector para escrita.")
                        }

                        // Converter String para ByteArray de 16 bytes
                        val dataBytes = ByteArray(16)
                        val sourceBytes = data.toByteArray(Charsets.UTF_8)
                        System.arraycopy(
                            sourceBytes,
                            0,
                            dataBytes,
                            0,
                            minOf(sourceBytes.size, 16)
                        )

                        val keyType: Byte = if (usedKeyType == "Key A") 0x60 else 0x61
                        
                        Log.d("MIFARE_WRITE", "Escrevendo no bloco $block...")
                        
                        try {
                            mifareProvider.writeBlock(block.toByte(), keyType, dataBytes)
                            Log.d("MIFARE_WRITE", "Escrita concluída")
                        } catch (writeEx: Exception) {
                            Log.e("MIFARE_WRITE_ERROR", "Erro na escrita", writeEx)
                            throw Exception("Erro ao escrever no bloco $block: ${writeEx.message}")
                        }
                        
                        mifareProvider.powerOff()
                        Log.d("MIFARE_POWEROFF", "Cartão desligado")

                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                        
                        val resultMap = mutableMapOf<String, Any>(
                            "success" to true,
                            "block" to block,
                            "sector" to sector,
                            "dataWritten" to data,
                            "authMethod" to "$usedKeyType - $usedKey",
                            "message" to "Dados escritos com sucesso usando $usedKeyType"
                        )
                        
                        callback(Result.Success(resultMap))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                            Log.e("MIFARE_POWEROFF_ERROR", "Erro ao desligar cartão", ignored)
                        }
                        
                        val errorMsg = e.message ?: "Erro desconhecido ao escrever no cartão"
                        Log.e("MIFARE_WRITE_ERROR", errorMsg, e)
                        
                        callback(Result.Error(Exception(errorMsg)))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.message ?: "Unknown error")
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()
            Log.d("MIFARE_EXECUTE", "Aguardando cartão para escrita...")

        } catch (e: Exception) {
            val errorMsg = "Exceção ao inicializar escrita: ${e.message}"
            Log.e("MIFARE_EXCEPTION", errorMsg, e)
            callback(Result.Error(Exception(errorMsg)))
        }
    }
}
