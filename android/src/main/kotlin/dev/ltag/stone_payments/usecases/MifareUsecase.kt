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
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // Ativar o cartão
                        mifareProvider.activateCard()

                        // Ler o bloco - teste a assinatura que funcionou
                        // OPÇÃO 1: Se for só o número do bloco
                        // val dataBytes = mifareProvider.readBlock(block)
                        
                        // OPÇÃO 2: Se precisar converter para Byte
                        // val dataBytes = mifareProvider.readBlock(block.toByte())
                        
                        // TESTE ESTE:
                        val blockByte = block.toByte()
                        val dataBytes = mifareProvider.readBlock(blockByte)

                        // Converter ByteArray para String legível
                        val dataString = if (dataBytes != null && dataBytes.isNotEmpty()) {
                            // Tenta ler como texto UTF-8
                            val textData = String(dataBytes, Charsets.UTF_8).trim()
                            
                            // Se tiver caracteres não imprimíveis, retorna como HEX
                            if (textData.any { it < ' ' || it > '~' }) {
                                dataBytes.joinToString("") { "%02X".format(it) }
                            } else {
                                textData
                            }
                        } else {
                            ""
                        }
                        
                        // Também retorna em HEX para debug
                        val dataHex = dataBytes?.joinToString(" ") { "%02X".format(it) } ?: ""

                        mifareProvider.powerOff()

                        Log.d("MIFARE_READ_SUCCESS", "Bloco $block lido: $dataString")
                        
                        val resultMap = mapOf(
                            "success" to true,
                            "block" to block,
                            "data" to dataString,
                            "dataHex" to dataHex,
                            "dataRaw" to (dataBytes?.toList() ?: emptyList<Byte>()),
                            "message" to "Cartão Mifare lido com sucesso"
                        )
                        
                        callback(Result.Success(resultMap))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.d("MIFARE_READ_ERROR", e.toString())
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.d("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.d("MIFARE_EXCEPTION", e.toString())
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
        callback: (Result<Map<String, Any>>) -> Unit
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
                        // Ativar o cartão
                        mifareProvider.activateCard()

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

                        // Escrever no bloco
                        val blockByte = block.toByte()
                        mifareProvider.writeBlock(blockByte, dataBytes)

                        mifareProvider.powerOff()

                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito: $data")
                        
                        val resultMap = mapOf(
                            "success" to true,
                            "block" to block,
                            "dataWritten" to data,
                            "message" to "Dados escritos com sucesso no bloco $block"
                        )
                        
                        callback(Result.Success(resultMap))

                    } catch (e: Exception) {
                        try {
                            mifareProvider.powerOff()
                        } catch (ignored: Exception) {
                        }
                        Log.d("MIFARE_WRITE_ERROR", e.toString())
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.d("MIFARE_CONNECTION_ERROR", error.toString())
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.d("MIFARE_EXCEPTION", e.toString())
            callback(Result.Error(e))
        }
    }
}
