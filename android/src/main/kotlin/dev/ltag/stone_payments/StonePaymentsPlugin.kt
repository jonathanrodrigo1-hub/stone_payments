package dev.ltag.stone_payments

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import dev.ltag.stone_payments.usecases.ActivateUsecase
import dev.ltag.stone_payments.usecases.PaymentUsecase
import dev.ltag.stone_payments.usecases.PrinterUsecase

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import stone.database.transaction.TransactionObject
import io.flutter.plugin.common.MethodChannel.Result as Res

// ========== NOVOS IMPORTS PARA MIFARE E DEVICE INFO ==========
import stone.providers.MifareProvider
import stone.utils.Stone
// ==============================================================

/** StonePaymentsPlugin */
class StonePaymentsPlugin : FlutterPlugin, MethodCallHandler, Activity() {
    private lateinit var channel: MethodChannel
    var context: Context = this;
    var transactionObject = TransactionObject()
    var paymentUsecase: PaymentUsecase? = null
    var printerUsecase: PrinterUsecase? = null

    companion object {
        var flutterBinaryMessenger: BinaryMessenger? = null
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        flutterBinaryMessenger = flutterPluginBinding.binaryMessenger;
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "stone_payments")
        channel.setMethodCallHandler(this)
        // Inicialize as propriedades aqui
        paymentUsecase = PaymentUsecase(this)
        printerUsecase = PrinterUsecase(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Res) {
        val activateUsecase: ActivateUsecase? = ActivateUsecase(context)
        when (call.method) {
            "activateStone" -> {
                try {
                    activateUsecase!!.doActivate(
                        call.argument("appName")!!,
                        call.argument("stoneCode")!!,
                        call.argument("qrCodeProviderId"),
                        call.argument("qrCodeAuthorization")
                    ) { resp ->
                        when (resp) {
                            is Result.Success<Boolean> -> result.success(
                                "Ativado"
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "payment" -> {
                try {
                    paymentUsecase!!.doPayment(
                        call.argument("value")!!,
                        call.argument("typeTransaction")!!,
                        call.argument("installment")!!,
                        call.argument("printReceipt"),
                    ) { resp ->
                        when (resp) {
                            is Result.Success<Boolean> -> result.success(
                                resp.data
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "transaction" -> {
                try {
                    paymentUsecase!!.doTransaction(
                        call.argument("value")!!,
                        call.argument("typeTransaction")!!,
                        call.argument("installment")!!,
                        call.argument("printReceipt"),
                    ) { resp ->
                        when (resp) {
                            is Result.Success<String> -> result.success(
                                resp.data
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "print" -> {
                try {
                    printerUsecase!!.print(
                        call.argument("items")!!,
                    ) { resp ->
                        when (resp) {
                            is Result.Success<Boolean> -> result.success(
                                "Impresso"
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "printReceipt" -> {
                try {
                    printerUsecase!!.printReceipt(
                        call.argument("type")!!,
                    ) { resp ->
                        when (resp) {
                            is Result.Success<Boolean> -> result.success(
                                "Via Impressa"
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "abortPayment" -> {
                try {
                    paymentUsecase!!.doAbort() { resp ->
                        when (resp) {
                            is Result.Success<String> -> result.success(
                                resp.data
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot Activate", e.toString())
                }
            }
            "cancelPayment" -> {
                try {
                    paymentUsecase!!.doCancelWithITK(
                       call.argument("initiatorTransactionKey")!!,
                       call.argument("printReceipt"),
                   ) { resp ->
                        when (resp) {
                            is Result.Success<*> -> result.success(resp.data.toString())
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot cancel", e.toString())
                }
            }
            "cancelPaymentWithAuthorizationCode" -> {
                try {
                    paymentUsecase!!.doCancelWithAuthorizationCode(
                       call.argument("authorizationCode")!!,
                       call.argument("printReceipt"),
                   ) { resp ->
                        when (resp) {
                            is Result.Success<*> -> result.success(resp.data.toString())
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot cancel", e.toString())
                }
            }
            
            // ========== NOVOS MÉTODOS - MIFARE E DEVICE INFO ==========
            "readMifareCard" -> readMifareCard(call, result)
            "writeMifareCard" -> writeMifareCard(call, result)
            "getDeviceSerial" -> getDeviceSerial(result)
            "getDeviceInfo" -> getDeviceInfo(result)
            // ===========================================================
            
            else -> {
                result.notImplemented()
            }
        }
    }

    // ============================================================
    // NOVOS MÉTODOS IMPLEMENTADOS
    // ============================================================
    
    /**
     * Lê os dados de um cartão Mifare
     */
    private fun readMifareCard(call: MethodCall, result: Res) {
        try {
            val timeout = call.argument<Int>("timeout") ?: 30
            
            val mifareProvider = MifareProvider(context, timeout)
            
            mifareProvider.setConnectionCallback(object : stone.providers.BaseProviderCallback() {
                override fun onSuccess() {
                    try {
                        val cardData = mifareProvider.mifareCardData
                        
                        val responseMap = hashMapOf<String, Any?>(
                            "uid" to (cardData?.uid ?: ""),
                            "type" to (cardData?.type ?: ""),
                            "dataRead" to (cardData?.dataRead ?: ""),
                            "success" to true
                        )
                        
                        result.success(responseMap)
                    } catch (e: Exception) {
                        result.error("MIFARE_READ_ERROR", "Erro ao processar dados do cartão", e.message)
                    }
                }

                override fun onError() {
                    val errorMessage = mifareProvider.theListOfErrors?.joinToString(", ") ?: "Erro desconhecido"
                    result.error(
                        "MIFARE_READ_ERROR",
                        "Erro ao ler cartão Mifare: $errorMessage",
                        null
                    )
                }
            })
            
            mifareProvider.execute()
            
        } catch (e: Exception) {
            result.error("MIFARE_READ_EXCEPTION", "Exceção ao ler cartão Mifare", e.message)
        }
    }

    /**
     * Escreve dados em um cartão Mifare
     */
    private fun writeMifareCard(call: MethodCall, result: Res) {
        try {
            val data = call.argument<String>("data")
            val block = call.argument<Int>("block") ?: 0
            val timeout = call.argument<Int>("timeout") ?: 30
            
            if (data == null) {
                result.error("INVALID_ARGUMENT", "Data cannot be null", null)
                return
            }
            
            val mifareProvider = MifareProvider(context, timeout)
            
            // Configura os dados para escrita
            mifareProvider.setDataToWrite(data, block)
            
            mifareProvider.setConnectionCallback(object : stone.providers.BaseProviderCallback() {
                override fun onSuccess() {
                    result.success(hashMapOf(
                        "success" to true,
                        "message" to "Dados escritos com sucesso no bloco $block"
                    ))
                }

                override fun onError() {
                    val errorMessage = mifareProvider.theListOfErrors?.joinToString(", ") ?: "Erro desconhecido"
                    result.error(
                        "MIFARE_WRITE_ERROR",
                        "Erro ao escrever no cartão Mifare: $errorMessage",
                        null
                    )
                }
            })
            
            mifareProvider.execute()
            
        } catch (e: Exception) {
            result.error("MIFARE_WRITE_EXCEPTION", "Exceção ao escrever no cartão Mifare", e.message)
        }
    }

    /**
     * Captura o número serial do dispositivo Stone
     */
    private fun getDeviceSerial(result: Res) {
        try {
            val deviceSerial = Stone.getPinpadFromListAt(0)?.serialNumber
            
            if (deviceSerial != null && deviceSerial.isNotEmpty()) {
                result.success(hashMapOf(
                    "success" to true,
                    "serial" to deviceSerial,
                    "message" to "Serial capturado com sucesso"
                ))
            } else {
                result.error(
                    "DEVICE_NOT_FOUND",
                    "Nenhum dispositivo Stone conectado ou serial não disponível",
                    null
                )
            }
        } catch (e: Exception) {
            result.error("SERIAL_ERROR", "Erro ao capturar serial do dispositivo", e.message)
        }
    }

    /**
     * Captura informações completas do dispositivo Stone
     */
    private fun getDeviceInfo(result: Res) {
        try {
            val device = Stone.getPinpadFromListAt(0)
            
            if (device != null) {
                val deviceInfo = hashMapOf<String, Any?>(
                    "serialNumber" to (device.serialNumber ?: ""),
                    "model" to (device.model ?: ""),
                    "manufacturer" to (device.manufacturer ?: ""),
                    "name" to (device.name ?: ""),
                    "isConnected" to device.isConnected
                )
                
                result.success(hashMapOf(
                    "success" to true,
                    "deviceInfo" to deviceInfo,
                    "message" to "Informações do dispositivo capturadas com sucesso"
                ))
            } else {
                result.error(
                    "DEVICE_NOT_FOUND",
                    "Nenhum dispositivo Stone conectado",
                    null
                )
            }
        } catch (e: Exception) {
            result.error("DEVICE_INFO_ERROR", "Erro ao capturar informações do dispositivo", e.message)
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
