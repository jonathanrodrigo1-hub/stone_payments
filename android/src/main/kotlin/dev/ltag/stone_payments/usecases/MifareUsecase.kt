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
                        
                        // Chave padrão Mifare (FFFFFFFFFFFF)
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        Log.d("MIFARE_AUTH", "Autenticando setor $sector...")
                        
                        // Autenticar o setor
                        try {
                            mifareProvider.authenticateSector(
                                MifareKeyType.TypeA,
                                defaultKey,
                                sector.toByte()
                            )
                            Log.d("MIFARE_AUTH", "Autenticação bem-sucedida")
                        } catch (authEx: Exception) {
                            Log.e("MIFARE_AUTH_ERROR", "Erro na autenticação", authEx)
                            throw Exception("Falha na autenticação do setor $sector. Verifique se a chave está correta. Erro: ${authEx.message}")
                        }

                        // Criar buffer para receber os dados
                        val dataBytes = ByteArray(16)
                        
                        Log.d("MIFARE_READ", "Lendo bloco $block...")
                        
                        // Ler o bloco
                        val keyType: Byte = 0x60 // Key A
                        
                        try {
                            mifareProvider.readBlock(block.toByte(), keyType, dataBytes)
                            Log.d("MIFARE_READ", "Bloco lido com sucesso")
                        } catch (readEx: Exception) {
                            Log.e("MIFARE_READ_ERROR", "Erro ao ler bloco", readEx)
                            throw Exception("Erro ao ler bloco $block: ${readEx.message ?: "Erro desconhecido"}")
                        }
                        
                        // Verificar se os dados foram lidos
                        if (dataBytes.all { it == 0.toByte() }) {
                            Log.w("MIFARE_EMPTY", "Bloco retornou vazio (todos zeros)")
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
                            "dataBytes" to dataBytes.size,
                            "message" to "Cartão Mifare lido com sucesso"
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
                        Log.e("MIFARE_READ_ERROR_TYPE", "Exception type: ${e.javaClass.simpleName}")
                        
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
                        
                        // Chave padrão
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        Log.d("MIFARE_AUTH", "Autenticando setor $sector...")
                        
                        try {
                            mifareProvider.authenticateSector(
                                MifareKeyType.TypeA,
                                defaultKey,
                                sector.toByte()
                            )
                            Log.d("MIFARE_AUTH", "Autenticação bem-sucedida")
                        } catch (authEx: Exception) {
                            Log.e("MIFARE_AUTH_ERROR", "Erro na autenticação", authEx)
                            throw Exception("Falha na autenticação do setor $sector: ${authEx.message}")
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
                        
                        Log.d("MIFARE_WRITE_DATA", "Dados preparados: ${dataBytes.size} bytes")

                        val keyType: Byte = 0x60
                        
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
                            "bytesWritten" to dataBytes.size,
                            "message" to "Dados escritos com sucesso no bloco $block"
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
