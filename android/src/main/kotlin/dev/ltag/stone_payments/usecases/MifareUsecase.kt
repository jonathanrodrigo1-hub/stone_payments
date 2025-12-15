package dev.ltag.stone_payments.usecases

import android.util.Log
import dev.ltag.stone_payments.Result
import br.com.stone.posandroid.providers.PosMifareProvider
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

                        // 3. Autenticar o setor (Key A = 0x60)
                        try {
                            mifareProvider.authenticateSector(
                                sector.toByte(),
                                DEFAULT_KEY,
                                0x60.toByte()  // 0x60 = Key A, 0x61 = Key B
                            )
                            Log.d("MIFARE", "Autenticação setor $sector OK")
                        } catch (authEx: Exception) {
                            Log.e("MIFARE", "Falha autenticação: ${authEx.message}")
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector: ${authEx.message}")))
                            return
                        }

                        // 4. Ler o bloco
                        val readBuffer = ByteArray(16)
                        mifareProvider.readBlock(sector.toByte(), block.toByte(), readBuffer)
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

                        // Autenticar o setor (Key A = 0x60)
                        try {
                            mifareProvider.authenticateSector(
                                sector.toByte(),
                                DEFAULT_KEY,
                                0x60.toByte()
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

                        mifareProvider.writeBlock(sector.toByte(), block.toByte(), dataBytes)
                        Log.d("MIFARE", "Escrita bloco $block concluída")

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
