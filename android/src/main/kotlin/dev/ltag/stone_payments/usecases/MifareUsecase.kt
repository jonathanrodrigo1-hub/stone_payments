package dev.ltag.stone_payments.usecases

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

    // Chave padrão Mifare (factory default)
    private val DEFAULT_KEY = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

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
                        // 1. Ativar o cartão
                        mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado")

                        // 2. Calcular o setor
                        val sector = block / 4
                        val relativeBlock = block % 4  // Bloco relativo dentro do setor (0-3)

                        // 3. Autenticar o setor
                        try {
                            val keyType = try {
                                MifareKeyType.valueOf("KEY_A")
                            } catch (e: Exception) {
                                try {
                                    MifareKeyType.valueOf("A")
                                } catch (e2: Exception) {
                                    MifareKeyType.values()[0]
                                }
                            }
                            
                            mifareProvider.authenticateSector(
                                keyType,
                                DEFAULT_KEY,
                                sector.toByte()
                            )
                            Log.d("MIFARE", "Autenticação setor $sector OK")
                        } catch (authEx: Exception) {
                            Log.e("MIFARE", "Falha autenticação: ${authEx.message}")
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector: ${authEx.message}")))
                            return
                        }

                        // 4. Ler o bloco - tenta com bloco relativo
                        val readBuffer = ByteArray(16)
                        try {
                            Log.d("MIFARE", "Tentando ler - Setor: $sector, Bloco relativo: $relativeBlock, Bloco absoluto: $block")
                            mifareProvider.readBlock(relativeBlock.toByte(), readBuffer)
                            Log.d("MIFARE", "Leitura OK com bloco relativo")
                        } catch (e1: Exception) {
                            Log.e("MIFARE", "Falha com bloco relativo, tentando bloco absoluto: ${e1.message}")
                            mifareProvider.readBlock(block.toByte(), readBuffer)
                            Log.d("MIFARE", "Leitura OK com bloco absoluto")
                        }
                        
                        Log.d("MIFARE", "Dados do bloco $block: ${byteArrayToHex(readBuffer)}")

                        // 5. Desligar o cartão
                        mifareProvider.powerOff()

                        // Converter para String hexadecimal
                        val hexString = byteArrayToHex(readBuffer)
                        
                        // Tentar converter para texto
                        val textString = try {
                            String(readBuffer, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                        } catch (e: Exception) {
                            ""
                        }

                        Log.d("MIFARE_READ_SUCCESS", "Bloco $block - Hex: $hexString, Text: $textString")
                        
                        val resultJson = """{"hex":"$hexString","text":"$textString","block":$block}"""
                        callback(Result.Success(resultJson))

                    } catch (e: Exception) {
                        try { mifareProvider.powerOff() } catch (ignored: Exception) {}
                        Log.e("MIFARE_READ_ERROR", "Erro: ${e.message}", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção: ${e.message}", e)
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
            if (block % 4 == 3) {
                callback(Result.Error(Exception("Bloco $block é um trailer block. Use blocos: 1, 2, 4, 5, 6, etc.")))
                return
            }

            if (data.toByteArray(Charsets.UTF_8).size > 16) {
                callback(Result.Error(Exception("Dados excedem 16 bytes")))
                return
            }

            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado")

                        val sector = block / 4
                        val relativeBlock = block % 4

                        // Autenticar o setor
                        try {
                            val keyType = try {
                                MifareKeyType.valueOf("KEY_A")
                            } catch (e: Exception) {
                                try {
                                    MifareKeyType.valueOf("A")
                                } catch (e2: Exception) {
                                    MifareKeyType.values()[0]
                                }
                            }
                            
                            mifareProvider.authenticateSector(
                                keyType,
                                DEFAULT_KEY,
                                sector.toByte()
                            )
                            Log.d("MIFARE", "Autenticação setor $sector OK")
                        } catch (authEx: Exception) {
                            Log.e("MIFARE", "Falha autenticação: ${authEx.message}")
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector: ${authEx.message}")))
                            return
                        }

                        val dataBytes = ByteArray(16) { 0x00 }
                        val sourceBytes = data.toByteArray(Charsets.UTF_8)
                        System.arraycopy(sourceBytes, 0, dataBytes, 0, minOf(sourceBytes.size, 16))

                        // Escrever o bloco - tenta com bloco relativo
                        try {
                            Log.d("MIFARE", "Tentando escrever - Setor: $sector, Bloco relativo: $relativeBlock, Bloco absoluto: $block")
                            mifareProvider.writeBlock(relativeBlock.toByte(), dataBytes)
                            Log.d("MIFARE", "Escrita OK com bloco relativo")
                        } catch (e1: Exception) {
                            Log.e("MIFARE", "Falha com bloco relativo, tentando bloco absoluto: ${e1.message}")
                            mifareProvider.writeBlock(block.toByte(), dataBytes)
                            Log.d("MIFARE", "Escrita OK com bloco absoluto")
                        }

                        mifareProvider.powerOff()

                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                        callback(Result.Success(true))

                    } catch (e: Exception) {
                        try { mifareProvider.powerOff() } catch (ignored: Exception) {}
                        Log.e("MIFARE_WRITE_ERROR", "Erro: ${e.message}", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção: ${e.message}", e)
            callback(Result.Error(e))
        }
    }

    /**
     * Detecta cartão Mifare e retorna info
     */
    fun readCardUID(callback: (Result<String>) -> Unit) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        mifareProvider.activateCard()
                        mifareProvider.powerOff()
                        Log.d("MIFARE_UID", "Cartão detectado com sucesso")
                        callback(Result.Success("CARD_DETECTED"))
                    } catch (e: Exception) {
                        try { mifareProvider.powerOff() } catch (ignored: Exception) {}
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    callback(Result.Error(Exception("Erro ao detectar cartão")))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            callback(Result.Error(e))
        }
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
