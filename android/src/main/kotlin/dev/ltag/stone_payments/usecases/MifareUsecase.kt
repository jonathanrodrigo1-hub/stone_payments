package dev.ltag.stone_payments.usecases

import android.content.Context
import android.util.Log
import dev.ltag.stone_payments.Result
import br.com.stone.posandroid.providers.PosMifareProvider
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
                        // Cada setor tem 4 blocos (0-3, 4-7, 8-11, etc)
                        val sector = block / 4
                        
                        // Autenticar o setor com a chave padrão (geralmente KEY_A com valor 0xFFFFFFFFFFFF)
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte()
                        )
                        
                        val authenticated = mifareProvider.authenticateSector(
                            sector.toByte(),
                            PosMifareProvider.KEY_A, // ou KEY_B dependendo da configuração
                            defaultKey
                        )
                        
                        if (!authenticated) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // Ler o bloco
                        val data = mifareProvider.readBlock(block.toByte())
                        
                        // Desligar o cartão
                        mifareProvider.powerOff()
                        
                        if (data != null && data.isNotEmpty()) {
                            // Converter ByteArray para String hexadecimal ou UTF-8
                            val dataString = data.joinToString("") { 
                                String.format("%02X", it) 
                            }
                            
                            Log.d("MIFARE_READ_SUCCESS", "Bloco $block lido: $dataString")
                            callback(Result.Success(dataString))
                        } else {
                            Log.d("MIFARE_READ_EMPTY", "Bloco $block retornou vazio")
                            callback(Result.Error(Exception("Dados vazios no bloco $block")))
                        }

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
                        val sector = block / 4
                        
                        // Verificar se não é um trailer block (blocos 3, 7, 11, 15, etc)
                        // Trailer blocks contêm as chaves de acesso e não devem ser escritos normalmente
                        if (block % 4 == 3) {
                            mifareProvider.powerOff()
                            callback(Result.Error(
                                Exception("Bloco $block é um trailer block e não pode ser escrito diretamente")
                            ))
                            return
                        }
                        
                        // Autenticar o setor com a chave padrão
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte(), 
                            0xFF.toByte()
                        )
                        
                        val authenticated = mifareProvider.authenticateSector(
                            sector.toByte(),
                            PosMifareProvider.KEY_A,
                            defaultKey
                        )
                        
                        if (!authenticated) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // Preparar dados para escrita (16 bytes)
                        val dataBytes = ByteArray(16)
                        
                        // Se data é hexadecimal
                        if (data.matches(Regex("^[0-9A-Fa-f]+$")) && data.length <= 32) {
                            // Converter hex string para bytes
                            val hexData = if (data.length % 2 != 0) "0$data" else data
                            for (i in hexData.indices step 2) {
                                val index = i / 2
                                if (index < 16) {
                                    dataBytes[index] = hexData.substring(i, i + 2).toInt(16).toByte()
                                }
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

                        // Escrever no bloco
                        val writeSuccess = mifareProvider.writeBlock(block.toByte(), dataBytes)
                        
                        // Desligar o cartão
                        mifareProvider.powerOff()
                        
                        if (writeSuccess) {
                            Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                            callback(Result.Success(true))
                        } else {
                            Log.e("MIFARE_WRITE_FAILED", "Falha ao escrever no bloco $block")
                            callback(Result.Error(Exception("Falha ao escrever no bloco $block")))
                        }

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
