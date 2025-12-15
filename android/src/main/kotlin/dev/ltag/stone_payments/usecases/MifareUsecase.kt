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
                        val cardInfo: ByteArray? = mifareProvider.activateCard()
                        val cardInfoStr = cardInfo?.let { byteArrayToHex(it) } ?: "null"
                        Log.d("MIFARE", "Cartão ativado: $cardInfoStr")

                        // 2. Calcular o setor baseado no bloco
                        val sector = block / 4

                        // 3. Autenticar o setor com Key A
                        val authResult: Boolean = mifareProvider.authenticateSector(
                            sector.toByte(),
                            DEFAULT_KEY,
                            MifareKeyType.TYPE_A
                        )
                        Log.d("MIFARE", "Autenticação setor $sector: $authResult")

                        if (authResult == false) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // 4. Ler o bloco - readBlock(sector, block, data)
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
                        
                        // Retornar JSON com ambos formatos
                        val resultJson = """{"hex":"$hexString","text":"$textString","block":$block}"""
                        callback(Result.Success(resultJson))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {}
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
            // Validar se não é bloco de trailer
            if (block % 4 == 3) {
                callback(Result.Error(Exception("Bloco $block é um trailer block. Use blocos: 1, 2, 4, 5, 6, etc.")))
                return
            }

            // Validar tamanho
            if (data.toByteArray(Charsets.UTF_8).size > 16) {
                callback(Result.Error(Exception("Dados excedem 16 bytes")))
                return
            }

            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // 1. Ativar o cartão
                        val cardInfo: ByteArray? = mifareProvider.activateCard()
                        val cardInfoStr = cardInfo?.let { byteArrayToHex(it) } ?: "null"
                        Log.d("MIFARE", "Cartão ativado: $cardInfoStr")

                        // 2. Calcular o setor
                        val sector = block / 4

                        // 3. Autenticar o setor
                        val authResult: Boolean = mifareProvider.authenticateSector(
                            sector.toByte(),
                            DEFAULT_KEY,
                            MifareKeyType.TYPE_A
                        )
                        Log.d("MIFARE", "Autenticação setor $sector: $authResult")

                        if (authResult == false) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // 4. Preparar dados (pad para 16 bytes)
                        val dataBytes = ByteArray(16) { 0x00 }
                        val sourceBytes = data.toByteArray(Charsets.UTF_8)
                        System.arraycopy(sourceBytes, 0, dataBytes, 0, minOf(sourceBytes.size, 16))

                        // 5. Escrever no bloco - writeBlock(sector, block, data)
                        mifareProvider.writeBlock(sector.toByte(), block.toByte(), dataBytes)
                        Log.d("MIFARE", "Escrita bloco $block concluída")

                        // 6. Desligar o cartão
                        mifareProvider.powerOff()

                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                        callback(Result.Success(true))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {}
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
     * Lê o UID do cartão Mifare
     */
    fun readCardUID(callback: (Result<String>) -> Unit) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        val cardUID: ByteArray? = mifareProvider.activateCard()
                        mifareProvider.powerOff()

                        if (cardUID != null && cardUID.size > 0) {
                            val uidHex = byteArrayToHex(cardUID)
                            Log.d("MIFARE_UID", "UID: $uidHex")
                            callback(Result.Success(uidHex))
                        } else {
                            callback(Result.Error(Exception("UID do cartão não disponível")))
                        }

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

    /**
     * Converte ByteArray para String hexadecimal
     */
    private fun byteArrayToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
