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
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // Ativar o cartão
                        mifareProvider.activateCard()

                        // Calcular o setor a partir do bloco
                        val sector = block / 4
                        
                        // Chave padrão Mifare (FFFFFFFFFFFF)
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        // Autenticar o setor com Key A
                        // authenticateSector(sector: Byte, key: ByteArray, keyType: MifareKeyType)
                        mifareProvider.authenticateSector(
                            sector.toByte(),
                            defaultKey,
                            MifareKeyType.TypeA
                        )

                        // Criar buffer para receber os dados
                        val dataBytes = ByteArray(16)
                        
                        // Ler o bloco
                        val keyType: Byte = 0x60
                        mifareProvider.readBlock(block.toByte(), keyType, dataBytes)

                        // Converter para String
                        val dataString = String(dataBytes, Charsets.UTF_8).trim()
                        
                        // Converter para HEX
                        val hexString = StringBuilder()
                        for (b in dataBytes) {
                            hexString.append(String.format("%02X ", b))
                        }

                        mifareProvider.powerOff()

                        Log.d("MIFARE_READ_SUCCESS", "Bloco $block lido: $dataString")
                        Log.d("MIFARE_READ_HEX", "HEX: $hexString")
                        
                        val resultMap = mutableMapOf<String, Any>(
                            "success" to true,
                            "block" to block,
                            "data" to dataString,
                            "dataHex" to hexString.toString().trim(),
                            "message" to "Cartão Mifare lido com sucesso"
                        )
                        
                        callback(Result.Success(resultMap))

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
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.message ?: "Unknown error")
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção ao ler Mifare", e)
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
                callback(Result.Error(Exception("Data must be 16 bytes or less. Current: ${data.length}")))
                return
            }

            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // Ativar o cartão
                        mifareProvider.activateCard()

                        // Calcular o setor a partir do bloco
                        val sector = block / 4
                        
                        // Chave padrão Mifare (FFFFFFFFFFFF)
                        val defaultKey = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )
                        
                        // Autenticar o setor com Key A
                        mifareProvider.authenticateSector(
                            sector.toByte(),
                            defaultKey,
                            MifareKeyType.TypeA
                        )

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
                        val keyType: Byte = 0x60
                        mifareProvider.writeBlock(block.toByte(), keyType, dataBytes)

                        mifareProvider.powerOff()

                        Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito: $data")
                        
                        val resultMap = mutableMapOf<String, Any>(
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
                        Log.e("MIFARE_WRITE_ERROR", "Erro ao escrever bloco $block", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val error = Exception("Erro ao detectar cartão Mifare. Aproxime o cartão do leitor.")
                    Log.e("MIFARE_CONNECTION_ERROR", error.message ?: "Unknown error")
                    callback(Result.Error(error))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Exceção ao escrever Mifare", e)
            callback(Result.Error(e))
        }
    }
}
