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

    // Chave padrão Mifare (factory default)
    private val DEFAULT_KEY = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    /**
     * Lê dados de um cartão Mifare
     * @param block Número do bloco a ser lido (0-63 para Mifare 1K)
     * @param timeout Timeout em segundos
     * @param keyType Tipo da chave: 0 = Key A, 1 = Key B
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
                        val cardInfo = mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado: ${cardInfo?.contentToString()}")

                        // 2. Calcular o setor baseado no bloco
                        // Mifare 1K: blocos 0-63, 4 blocos por setor = 16 setores
                        val sector = block / 4

                        // 3. Autenticar o setor com Key A (tipo 0)
                        val authResult = mifareProvider.authenticateSector(
                            sector.toByte(),
                            DEFAULT_KEY,
                            0.toByte() // 0 = Key A, 1 = Key B
                        )
                        Log.d("MIFARE", "Autenticação setor $sector: $authResult")

                        if (!authResult) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // 4. Ler o bloco
                        val blockData = mifareProvider.readBlock(block.toByte())
                        Log.d("MIFARE", "Dados do bloco $block: ${blockData?.contentToString()}")

                        // 5. Desligar o cartão
                        mifareProvider.powerOff()

                        if (blockData != null && blockData.isNotEmpty()) {
                            // Converter para String hexadecimal para visualização
                            val hexString = blockData.joinToString("") { "%02X".format(it) }
                            
                            // Tentar converter para texto (se for texto legível)
                            val textString = try {
                                String(blockData, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                            } catch (e: Exception) {
                                ""
                            }

                            Log.d("MIFARE_READ_SUCCESS", "Bloco $block - Hex: $hexString, Text: $textString")
                            
                            // Retornar JSON com ambos formatos
                            val resultJson = """{"hex":"$hexString","text":"$textString","block":$block}"""
                            callback(Result.Success(resultJson))
                        } else {
                            callback(Result.Error(Exception("Bloco $block retornou vazio")))
                        }

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
     * @param data Dados a serem escritos (máximo 16 bytes)
     * @param block Número do bloco (evite blocos de trailer: 3, 7, 11, 15, etc)
     * @param timeout Timeout em segundos
     */
    fun writeMifareCard(
        data: String,
        block: Int,
        timeout: Int,
        callback: (Result<Boolean>) -> Unit
    ) {
        try {
            // Validar se não é bloco de trailer (contém as chaves)
            if (block % 4 == 3) {
                callback(Result.Error(Exception("Bloco $block é um trailer block (contém chaves). Use blocos de dados: 1, 2, 4, 5, 6, etc.")))
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
                        val cardInfo = mifareProvider.activateCard()
                        Log.d("MIFARE", "Cartão ativado: ${cardInfo?.contentToString()}")

                        // 2. Calcular o setor
                        val sector = block / 4

                        // 3. Autenticar o setor
                        val authResult = mifareProvider.authenticateSector(
                            sector.toByte(),
                            DEFAULT_KEY,
                            0.toByte() // Key A
                        )
                        Log.d("MIFARE", "Autenticação setor $sector: $authResult")

                        if (!authResult) {
                            mifareProvider.powerOff()
                            callback(Result.Error(Exception("Falha na autenticação do setor $sector")))
                            return
                        }

                        // 4. Preparar dados (pad para 16 bytes)
                        val dataBytes = ByteArray(16) { 0x00 }
                        val sourceBytes = data.toByteArray(Charsets.UTF_8)
                        System.arraycopy(sourceBytes, 0, dataBytes, 0, minOf(sourceBytes.size, 16))

                        // 5. Escrever no bloco
                        val writeResult = mifareProvider.writeBlock(block.toByte(), dataBytes)
                        Log.d("MIFARE", "Escrita bloco $block: $writeResult")

                        // 6. Desligar o cartão
                        mifareProvider.powerOff()

                        if (writeResult) {
                            Log.d("MIFARE_WRITE_SUCCESS", "Bloco $block escrito com sucesso")
                            callback(Result.Success(true))
                        } else {
                            callback(Result.Error(Exception("Falha ao escrever no bloco $block")))
                        }

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
     * Lê o UID (identificador único) do cartão Mifare
     */
    fun readCardUID(callback: (Result<String>) -> Unit) {
        try {
            val mifareProvider = PosMifareProvider(context)

            mifareProvider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    try {
                        // activateCard() retorna o UID do cartão
                        val cardUID = mifareProvider.activateCard()
                        mifareProvider.powerOff()

                        if (cardUID != null && cardUID.isNotEmpty()) {
                            val uidHex = cardUID.joinToString("") { "%02X".format(it) }
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
}
