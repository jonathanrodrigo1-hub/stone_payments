package dev.ltag.stone_payments

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import dev.ltag.stone_payments.usecases.ActivateUsecase
import dev.ltag.stone_payments.usecases.PaymentUsecase
import dev.ltag.stone_payments.usecases.PrinterUsecase
import dev.ltag.stone_payments.usecases.MifareUsecase

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import stone.database.transaction.TransactionObject
import io.flutter.plugin.common.MethodChannel.Result as Res

// ========== IMPORTS PARA DEVICE INFO ==========
import stone.utils.Stone
// ==============================================

/** StonePaymentsPlugin */
class StonePaymentsPlugin : FlutterPlugin, MethodCallHandler, Activity() {
    private lateinit var channel: MethodChannel
    var context: Context = this;
    var transactionObject = TransactionObject()
    var paymentUsecase: PaymentUsecase? = null
    var printerUsecase: PrinterUsecase? = null
    var mifareUsecase: MifareUsecase? = null

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
        mifareUsecase = MifareUsecase(this)
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
            
            // ========== MIFARE METHODS ==========
            "readMifareCard" -> {
                try {
                    val timeout = call.argument<Int>("timeout") ?: 30
                    val block = call.argument<Int>("block") ?: 4
                    
                    mifareUsecase!!.readMifareCard(block, timeout) { resp ->
                        when (resp) {
                            is Result.Success<String> -> result.success(
                                hashMapOf(
                                    "success" to true,
                                    "data" to resp.data,
                                    "block" to block,
                                    "message" to "Cartão Mifare lido com sucesso"
                                )
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot read Mifare", e.toString())
                }
            }
            "writeMifareCard" -> {
                try {
                    val data = call.argument<String>("data") ?: ""
                    val block = call.argument<Int>("block") ?: 4
                    val timeout = call.argument<Int>("timeout") ?: 30
                    
                    mifareUsecase!!.writeMifareCard(data, block, timeout) { resp ->
                        when (resp) {
                            is Result.Success<Boolean> -> result.success(
                                hashMapOf(
                                    "success" to true,
                                    "block" to block,
                                    "message" to "Dados escritos com sucesso no bloco $block"
                                )
                            )
                            else -> result.error("Error", resp.toString(), resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    result.error("UNAVAILABLE", "Cannot write Mifare", e.toString())
                }
            }
            
            // ========== DEVICE INFO ==========
            "getDeviceSerial" -> getDeviceSerial(result)
            "getDeviceInfo" -> getDeviceInfo(result)
            // =================================
            
            else -> {
                result.notImplemented()
            }
        }
    }

    // ============================================================
    // MÉTODOS DEVICE INFO
    // ============================================================
    
    /**
     * Captura o número serial do dispositivo Stone
     */
    private fun getDeviceSerial(result: Res) {
        try {
            val pinpadList = Stone.getPinpadListSize()
            
            if (pinpadList > 0) {
                val device = Stone.getPinpadFromListAt(0)
                val deviceSerial = device?.serialNumber
                
                if (deviceSerial != null && deviceSerial.isNotEmpty()) {
                    result.success(hashMapOf(
                        "success" to true,
                        "serial" to deviceSerial,
                        "message" to "Serial capturado com sucesso"
                    ))
                } else {
                    result.error(
                        "SERIAL_EMPTY",
                        "Serial do dispositivo está vazio",
                        null
                    )
                }
            } else {
                result.error(
                    "DEVICE_NOT_FOUND",
                    "Nenhum dispositivo Stone conectado",
                    null
                )
            }
        } catch (e: Exception) {
            result.error("SERIAL_ERROR", "Erro ao capturar serial do dispositivo: ${e.message}", null)
        }
    }

    /**
     * Captura informações completas do dispositivo Stone
     */
    private fun getDeviceInfo(result: Res) {
        try {
            val pinpadList = Stone.getPinpadListSize()
            
            if (pinpadList > 0) {
                val device = Stone.getPinpadFromListAt(0)
                
                if (device != null) {
                    val deviceInfo = hashMapOf<String, Any?>(
                        "serialNumber" to (device.serialNumber ?: ""),
                        "name" to (device.name ?: ""),
                        "manufacturer" to "Stone",
                        "pinpadListSize" to pinpadList
                    )
                    
                    result.success(hashMapOf(
                        "success" to true,
                        "deviceInfo" to deviceInfo,
                        "message" to "Informações do dispositivo capturadas com sucesso"
                    ))
                } else {
                    result.error(
                        "DEVICE_NULL",
                        "Dispositivo retornou null",
                        null
                    )
                }
            } else {
                result.error(
                    "DEVICE_NOT_FOUND",
                    "Nenhum dispositivo Stone conectado",
                    null
                )
            }
        } catch (e: Exception) {
            result.error("DEVICE_INFO_ERROR", "Erro ao capturar informações do dispositivo: ${e.message}", null)
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
