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
                        // Pegar UUID do cartão
                        val cardUUID = mifareProvider.cardUUID
                        val cardUUIDHex = cardUUID?.joinToString("") { byte -> 
                            String.format("%02X", byte) 
                        } ?: "UNKNOWN"
                        
                        Log.d("MIFARE", "Cartão detectado: $cardUUIDHex")
                        Log.d("MIFARE", "Iniciando leitura do bloco $block")

                        // Calcular setor e bloco relativo
                        val sector = block / 4
                        val relativeBlock = block % 4
                        
                        Log.d("MIFARE", "Setor: $sector, Bloco relativo: $relativeBlock")

                        // Chave padrão de autenticação
                        val key = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )

                        // Autenticar setor (ordem correta: MifareKeyType, key, sector)
                        try {
                            mifareProvider.authenticateSector(
                                MifareKeyType.TypeA, 
                                key, 
                                sector.toByte()
                            )
                            Log.d("MIFARE", "Setor $sector autenticado")
                        } catch (e: PosMifareProvider.MifareException) {
                            Log.e("MIFARE_AUTH_ERROR", "Erro na autenticação: ${e.errorEnum?.name}")
                            callback(Result.Error(Exception("Erro na autenticação: ${e.errorEnum?.name}")))
                            return
                        }

                        // Alocar ByteArray onde os dados serão escritos
                        val byteArray = ByteArray(16)
                        
                        // Ler o bloco (ordem: sector, relativeBlock, byteArray)
                        try {
                            mifareProvider.readBlock(
                                sector.toByte(), 
                                relativeBlock.toByte(), 
                                byteArray
                            )
                            Log.d("MIFARE", "Bloco lido com sucesso")
                        } catch (e: PosMifareProvider.MifareException) {
                            Log.e("MIFARE_READ_ERROR", "Erro na leitura: ${e.errorEnum?.name}")
                            callback(Result.Error(Exception("Erro na leitura: ${e.errorEnum?.name}")))
                            return
                        }

                        // Converter para String Hexadecimal
                        val dataHex = byteArray.joinToString("") { byte -> 
                            String.format("%02X", byte) 
                        }
                        
                        // Converter para String UTF-8 (removendo caracteres não imprimíveis)
                        val dataString = try {
                            String(byteArray, Charsets.UTF_8)
                                .trim()
                                .replace("\u0000", "")
                                .replace(Regex("[^\\x20-\\x7E]"), "")
                        } catch (e: Exception) {
                            ""
                        }

                        Log.d("MIFARE_DATA_HEX", dataHex)
                        Log.d("MIFARE_DATA_STRING", dataString)
                        Log.d("MIFARE_CARD_UUID", cardUUIDHex)

                        // Criar lista de bytes para JSON
                        val dataBytesList = byteArray.joinToString(",") { byte -> 
                            byte.toString() 
                        }

                        // Retornar dados em formato JSON
                        val jsonResult = """
                            {
                                "success": true,
                                "block": $block,
                                "sector": $sector,
                                "relativeBlock": $relativeBlock,
                                "cardUUID": "$cardUUIDHex",
                                "dataHex": "$dataHex",
                                "dataString": "$dataString",
                                "dataBytes": [$dataBytesList],
                                "message": "Bloco $block lido com sucesso"
                            }
                        """.trimIndent()

                        callback(Result.Success(jsonResult))

                    } catch (e: Exception) {
                        Log.e("MIFARE_GENERAL_ERROR", "Erro geral: ${e.message}", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val errors = mifareProvider.listOfErrors?.toString() ?: "Erro desconhecido"
                    Log.e("MIFARE_CONNECTION_ERROR", "Erro na detecção: $errors")
                    callback(Result.Error(Exception("Erro ao detectar cartão: $errors")))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Erro ao iniciar: ${e.message}", e)
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
                        // Pegar UUID do cartão
                        val cardUUID = mifareProvider.cardUUID
                        val cardUUIDHex = cardUUID?.joinToString("") { byte -> 
                            String.format("%02X", byte) 
                        } ?: "UNKNOWN"
                        
                        Log.d("MIFARE", "Cartão detectado: $cardUUIDHex")
                        Log.d("MIFARE", "Iniciando escrita no bloco $block")

                        // Calcular setor e bloco relativo
                        val sector = block / 4
                        val relativeBlock = block % 4
                        
                        Log.d("MIFARE", "Setor: $sector, Bloco relativo: $relativeBlock")

                        // Chave padrão
                        val key = byteArrayOf(
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                        )

                        // Autenticar setor
                        try {
                            mifareProvider.authenticateSector(
                                MifareKeyType.TypeA, 
                                key, 
                                sector.toByte()
                            )
                            Log.d("MIFARE", "Setor $sector autenticado")
                        } catch (e: PosMifareProvider.MifareException) {
                            Log.e("MIFARE_AUTH_ERROR", "Erro na autenticação: ${e.errorEnum?.name}")
                            callback(Result.Error(Exception("Erro na autenticação: ${e.errorEnum?.name}")))
                            return
                        }

                        // Preparar dados (16 bytes com padding de espaços)
                        val value = String.format("%-16s", data).toByteArray(Charsets.UTF_8)

                        // Escrever no bloco
                        try {
                            mifareProvider.writeBlock(
                                sector.toByte(), 
                                relativeBlock.toByte(), 
                                value
                            )
                            Log.d("MIFARE", "Dados escritos no bloco $block")
                        } catch (e: PosMifareProvider.MifareException) {
                            Log.e("MIFARE_WRITE_ERROR", "Erro na escrita: ${e.errorEnum?.name}")
                            callback(Result.Error(Exception("Erro na escrita: ${e.errorEnum?.name}")))
                            return
                        }

                        callback(Result.Success(true))

                    } catch (e: Exception) {
                        Log.e("MIFARE_GENERAL_ERROR", "Erro geral: ${e.message}", e)
                        callback(Result.Error(e))
                    }
                }

                override fun onError() {
                    val errors = mifareProvider.listOfErrors?.toString() ?: "Erro desconhecido"
                    Log.e("MIFARE_CONNECTION_ERROR", "Erro na detecção: $errors")
                    callback(Result.Error(Exception("Erro ao detectar cartão: $errors")))
                }
            }

            mifareProvider.execute()

        } catch (e: Exception) {
            Log.e("MIFARE_EXCEPTION", "Erro ao iniciar: ${e.message}", e)
            callback(Result.Error(e))
        }
    }
}
