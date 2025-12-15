package dev.ltag.stone_payments.usecases

import android.content.Context
import android.util.Log
import dev.ltag.stone_payments.Result
import br.com.stone.posandroid.providers.PosMifareProvider
import stone.application.enums.Action
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
                        Log.d("MIFARE", "Cartão detectado, iniciando leitura do bloco $block")
                        
                        // Ativar o cartão
                        mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado")

                        // Autenticar setor antes de ler
                        val sector = block / 4
                        val keyA = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        mifareProvider.authenticateSector(sector, keyA, Action.KEY_A)
                        Log.d("MIFARE", "Setor $sector autenticado")

                        // LER O BLOCO - AQUI ESTÁ O CÓDIGO QUE ESTAVA FALTANDO!
                        val data = mifareProvider.readBlock(block)
                        Log.d("MIFARE", "Dados lidos do bloco $block")

                        // Converter para String Hexadecimal e UTF-8
                        val dataHex = data.joinToString("") { byte -> "%02X".format(byte) }
                        val dataString = try {
                            String(data, Charsets.UTF_8).trim().replace("\u0000", "")
                        } catch (e: Exception) {
                            ""
                        }

                        Log.d("MIFARE_DATA_HEX", dataHex)
                        Log.d("MIFARE_DATA_STRING", dataString)

                        // Desenergisar o cartão
                        mifareProvider.powerOff()
                        Log.d("MIFARE", "Cartão desenergizado")

                        // Retornar dados em formato JSON
                        val jsonResult = """
                            {
                                "success": true,
                                "block": $block,
                                "dataHex": "$dataHex",
                                "dataString": "$dataString",
                                "dataBytes": [${data.joinToString(",")}],
                                "message": "Bloco $block lido com sucesso"
                            }
                        """.trimIndent()

                        callback(Result.Success(jsonResult))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.e("MIFARE_READ_ERROR", "Erro ao ler bloco: ${e.message}", e)
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
            Log.e("MIFARE_EXCEPTION", "Erro geral: ${e.message}", e)
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
            if (data.length > 16) {
                callback(Result.Error(Exception("Data must be 16 bytes or less")))
                return
            }

            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        Log.d("MIFARE", "Cartão detectado, iniciando escrita no bloco $block")
                        
                        // Ativar o cartão
                        mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado")

                        // Autenticar setor antes de escrever
                        val sector = block / 4
                        val keyA = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        mifareProvider.authenticateSector(sector, keyA, Action.KEY_A)
                        Log.d("MIFARE", "Setor $sector autenticado")

                        // Converter para ByteArray de 16 bytes
                        val dataBytes = ByteArray(16)
                        val sourceBytes = data.toByteArray(Charsets.UTF_8)
                        System.arraycopy(
                            sourceBytes,
                            0,
                            dataBytes,
                            0,
                            minOf(sourceBytes.size, 16)
                        )

                        // ESCREVER NO BLOCO
                        mifareProvider.writeBlock(block, dataBytes)
                        Log.d("MIFARE", "Dados escritos no bloco $block")

                        // Desenergisar o cartão
                        mifareProvider.powerOff()
                        Log.d("MIFARE", "Cartão desenergizado")

                        callback(Result.Success(true))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.e("MIFARE_WRITE_ERROR", "Erro ao escrever: ${e.message}", e)
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
            Log.e("MIFARE_EXCEPTION", "Erro geral: ${e.message}", e)
            callback(Result.Error(e))
        }
    }
}
