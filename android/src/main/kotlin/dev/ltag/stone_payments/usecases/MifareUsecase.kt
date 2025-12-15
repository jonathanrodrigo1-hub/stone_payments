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
        callback: (Result<String>) -> Unit
    ) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // Ativar o cartão
                        mifareProvider.activateCard()
                        
                        // Calcular o setor baseado no bloco
                        val sector = (block / 4).toByte()
                        
                        // Chave padrão Mifare (0xFFFFFFFFFFFF)
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte()
                        )
                        
                        // Autenticar o setor com KEY_A
                        mifareProvider.authenticateSector(
                            sector,
                            MifareKeyType.KEY_A,
                            defaultKey[0],
                            defaultKey[1],
                            defaultKey[2],
                            defaultKey[3],
                            defaultKey[4],
                            defaultKey[5]
                        )

                        // Ler o bloco - a API retorna void e usa callbacks
                        mifareProvider.readBlock(
                            block.toByte(),
                            object : PosMifareProvider.MifareReadCallback {
                                override fun onSuccess(data: ByteArray?) {
                                    try {
                                        mifareProvider.powerOff()
                                        
                                        if (data != null && data.size > 0) {
                                            // Converter ByteArray para String hexadecimal
                                            val hexString = StringBuilder()
                                            for (byte in data) {
                                                hexString.append(String.format("%02X", byte))
                                            }
                                            
                                            Log.d("MIFARE_READ_SUCCESS", "Bloco $block lido: $hexString")
                                            callback(Result.Success(hexString.toString()))
                                        } else {
                                            Log.d("MIFARE_READ_EMPTY", "Bloco $block retornou vazio")
                                            callback(Result.Error(Exception("Dados vazios no bloco $block")))
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MIFARE_READ_CALLBACK_ERROR", "Erro no callback", e)
                                        callback(Result.Error(e))
                                    }
                                }

                                override fun onError(message: String?) {
                                    try {
                                        mifareProvider.powerOff()
                                    } catch (ignored: Exception) {
                                    }
                                    val error = Exception("Erro ao ler bloco: ${message ?: "desconhecido"}")
                                    Log.e("MIFARE_READ_BLOCK_ERROR", error.toString())
                                    callback(Result.Error(error))
                                }
                            }
                        )

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.e("MIFARE_READ_ERROR", "Erro ao ler bloco $block", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare")
                    Log.e("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção geral no Mifare", e)
            callback(Result.Error(e))
        }
    }

    /**
     * Escreve dados em um cartão Mifare
     */
    fun writeMifareCard(
        data: String,
        block: Int,
        timeout: Int,
        callback: (Result<Boolean>) -> Unit
    ) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // Ativar o cartão
                        mifareProvider.activateCard()
                        
                        // Calcular o setor baseado no bloco
                        val sector = (block / 4).toByte()
                        
                        // Verificar se não é um trailer block
                        if (block % 4 == 3) {
                            mifareProvider.powerOff()
                            callback(Result.Error(
                                Exception("Bloco $block é um trailer block e não pode ser escrito diretamente")
                            ))
                            return
                        }
                        
                        // Chave padrão Mifare
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte()
                        )
                        
                        // Autenticar o setor com KEY_A
                        mifareProvider.authenticateSector(
                            sector,
                            MifareKeyType.KEY_A,
                            defaultKey[0],
                            defaultKey[1],
                            defaultKey[2],
                            defaultKey[3],
                            defaultKey[4],
                            defaultKey[5]
                        )

                        // Preparar dados para escrita (16 bytes)
                        val dataBytes = ByteArray(16)
                        
                        // Se data é hexadecimal
                        if (data.matches(Regex("^[0-9A-Fa-f]+$")) && data.length <= 32) {
                            // Converter hex string para bytes
                            val hexData = if (data.length % 2 != 0) "0$data" else data
                            var i = 0
                            while (i < hexData.length && i / 2 < 16) {
                                val index = i / 2
                                dataBytes[index] = hexData.substring(i, i + 2).toInt(16).toByte()
                                i += 2
                            }
                        } else {
                            // Tratar como texto UTF-8
                            if (data.length > 16) {
                                mifareProvider.powerOff()
                                callback(Result.Error(
                                    Exception("Dados devem ter no máximo 16 caracteres (16 bytes)")
                                ))
                                return
                            }
                            
                            val sourceBytes = data.toByteArray(Charsets.UTF_8)
                            System.arraycopy(
                                sourceBytes,
                                0,
                                dataBytes,
                                0,
                                minOf(sourceBytes.size, 16)
                            )
                        }

                        // Escrever no bloco - writeBlock provavelmente recebe os 16 bytes separados
                        mifareProvider.writeBlock(
                            block.toByte(),
                            dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3],
                            dataBytes[4], dataBytes[5], dataBytes[6], dataBytes[7],
                            dataBytes[8], dataBytes[9], dataBytes[10], dataBytes[11],
                            dataBytes[12], dataBytes[13], dataBytes[14], dataBytes[15]
                        )
                        
                        // Desligar o cartão
                        mifareProvider.powerOff()
                        
                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                        callback(Result.Success(true))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.e("MIFARE_WRITE_ERROR", "Erro ao escrever no bloco $block", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare")
                    Log.e("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção geral no Mifare", e)
            callback(Result.Error(e))
        }
    }
}
