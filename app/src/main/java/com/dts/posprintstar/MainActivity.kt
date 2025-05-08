package com.dts.posprintstar

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.starmicronics.stario10.InterfaceType
import com.starmicronics.stario10.StarConnectionSettings
import com.starmicronics.stario10.StarDeviceDiscoveryManager
import com.starmicronics.stario10.StarDeviceDiscoveryManagerFactory
import com.starmicronics.stario10.StarPrinter
import com.starmicronics.stario10.starxpandcommand.DocumentBuilder
import com.starmicronics.stario10.starxpandcommand.DrawerBuilder
import com.starmicronics.stario10.starxpandcommand.PrinterBuilder
import com.starmicronics.stario10.starxpandcommand.StarXpandCommandBuilder
import com.starmicronics.stario10.starxpandcommand.drawer.OpenParameter
import com.starmicronics.stario10.starxpandcommand.printer.CutType
import com.starmicronics.stario10.starxpandcommand.printer.ImageParameter
import com.starmicronics.stario10.starxpandcommand.printer.InternationalCharacterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess
import androidx.core.graphics.scale

class MainActivity : AppCompatActivity() {

    var lines = ArrayList<String>()

    lateinit var bmp : Bitmap

    var usbaddress = ""
    var macro_param = ""
    var line = ""

    var mDeviceList: HashMap<String, UsbDevice>? = null
    var mDevice: UsbDevice? = null
    var mPermissionIntent: PendingIntent? = null

    var Pmanager: StarDeviceDiscoveryManager? = null

    val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed( {  grantPermissions() }, 200)

        } catch (e:Exception) {
            msgclose((object : Any() {}.javaClass.enclosingMethod?.name ?: "") +". "+e.message)
        }
    }


    //region Events


    //endregion

    //region Main

   private fun startApplication() {
        try {

            if (!Environment.isExternalStorageManager()) {
                grandAllFilesAccess()
                return
            }

            val mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            mDeviceList = mUsbManager?.getDeviceList()
            val mDeviceIterator = mDeviceList?.values

            mPermissionIntent = PendingIntent.getBroadcast(this, 0,Intent(ACTION_USB_PERMISSION),PendingIntent.FLAG_IMMUTABLE )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(mUsbReceiver, filter)

            var usbDevice = ""

            for (itm in mDeviceList?.values!!) {
                val usbDevice1 = itm
                val interfaceCount = usbDevice1.interfaceCount
                mDevice = usbDevice1
            }

            mUsbManager!!.requestPermission(mDevice, mPermissionIntent)

        } catch (e: Exception) {
            msgclose((object : Any() {}.javaClass.enclosingMethod?.name ?: "") +" . "+e.message)
        }
    }

    fun getUsb()  {
        var usbname = ""

        try {
            val interfaceTypes = mutableListOf<InterfaceType>()
            interfaceTypes += InterfaceType.Usb

            this.Pmanager?.stopDiscovery()

            Pmanager = StarDeviceDiscoveryManagerFactory.create(
                interfaceTypes,
                applicationContext
            )
            Pmanager?.discoveryTime = 10000
            Pmanager?.callback = object : StarDeviceDiscoveryManager.Callback {
                override fun onPrinterFound(printer: StarPrinter) {
                    usbaddress=printer.connectionSettings.identifier
                    if (usbaddress.isNotEmpty()) {
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed( {  processPrint() }, 200)
                    } else {
                        msgclose("¡No está conectada ninguna impresora USB!");return
                    }
                }

                override fun onDiscoveryFinished() {}
            }

            Pmanager?.startDiscovery()
        } catch (e: Exception) {
            msgclose((object : Any() {}.javaClass.enclosingMethod?.name ?: "") +" . "+e.message)
        }

    }

    fun processPrint() {
        try {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed( { loadDoc() }, 100)
        } catch (e: Exception) {
            msgclose((object : Any() {}.javaClass.enclosingMethod?.name ?: "") +" . "+e.message)
        }
    }

    private fun loadDoc() {
        try {
            val file = File(Environment.getExternalStorageDirectory().toString() + "/print.txt")
            lines = file.readLines() as ArrayList<String>

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed( { printDocument() }, 1000)
        } catch (e: Exception) {
            msgclose((object : Any() {}.javaClass.enclosingMethod?.name ?: "") +" . "+e.message)
        }
    }

    private fun printDocument() {
        try {
            val connectionType = InterfaceType.Usb
            val printerSettings = StarConnectionSettings(connectionType, usbaddress)
            val starPrinter = StarPrinter(printerSettings, applicationContext)
            val coroutineJob = SupervisorJob()
            val coroutineScope = CoroutineScope(Dispatchers.Default + coroutineJob)

            var hasPrintedImage = false
            var imageBitmap: Bitmap
            var imagePathIndex = 0
            var imagePath = ""
            var wasSuccessful = true

            coroutineScope.launch {
                try {
                    val commandBuilder = StarXpandCommandBuilder()
                    val documentBuilder = DocumentBuilder()
                    val printerBuilder = PrinterBuilder()
                    val newLine = " \n"

                    val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logompos)

                    printerBuilder.styleInternationalCharacter(InternationalCharacterType.Usa)
                    printerBuilder.styleCharacterSpace(0.0)

                    for (lineItem in lines) {
                        line = lineItem
                        imagePathIndex = line.indexOf("@@pic")

                        if (imagePathIndex == 0) {
                            imagePath = line.substring(6)
                            try {
                                imageBitmap = loadImage(imagePath)
                                printerBuilder.actionPrintImage(ImageParameter(imageBitmap, 300))
                                hasPrintedImage = true
                            } catch (imageLoadException: Exception) {
                                toastlong((object {}.javaClass.enclosingMethod?.name ?: "") + " . " + imageLoadException.message)
                            }
                        } else {
                            printerBuilder.actionPrintText(lineItem + "\n")
                        }
                    }

                    if (hasPrintedImage) {
                        printerBuilder.actionPrintImage(ImageParameter(logoBitmap, 150))
                        printerBuilder.actionPrintText(newLine)
                    }

                    printerBuilder.actionCut(CutType.Full)
                    documentBuilder.addPrinter(printerBuilder)
                    documentBuilder.addDrawer(DrawerBuilder().actionOpen(OpenParameter()))

                    commandBuilder.addDocument(DocumentBuilder().addPrinter(printerBuilder))
                    val printCommands = commandBuilder.getCommands()

                    starPrinter.openAsync().await()
                    starPrinter.printAsync(printCommands).await()

                } catch (printException: Exception) {
                    wasSuccessful = false
                    msgclose((object {}.javaClass.enclosingMethod?.name ?: "") + " . " + printException.message)
                }

                try {
                    starPrinter.closeAsync().await()
                } catch (closeException: Exception) {
                    wasSuccessful = false
                    msgclose((object {}.javaClass.enclosingMethod?.name ?: "") + " . " + closeException.message)
                }

                if (wasSuccessful) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        exitProcess(0)
                    }, 300)
                }
            }

        } catch (outerException: Exception) {
            msgclose((object {}.javaClass.enclosingMethod?.name ?: "") + " . " + outerException.message)
        }
    }


    //endregion

    //region Permission

    private fun grantPermissions() {
        try {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startApplication()
            } else {
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),1)
            }
        } catch (e: java.lang.Exception) {
            toastlong((object : Any() {}.javaClass.enclosingMethod?.name ?: "") + " . " + e.message)
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        try {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startApplication()
            } else {
                super.finish()
            }
        } catch (e: java.lang.Exception) {
            toastlong((object : Any() {}.javaClass.enclosingMethod?.name ?: "") + " . " + e.message)
        }
    }

    fun grandAllFilesAccess() {
        try {
            val uri = Uri.parse("package:com.dts.posprintusb")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            startActivity(intent)
        } catch (ex: java.lang.Exception) {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            startActivity(intent)
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed( {  finish()  }, 50)
    }

    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)) {
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed( {
                            getUsb()
                        }, 250)
                    } else {
                        toast("PERMISO DE IMPRIMIR DENEGADO")
                    }
                }
            }
        }
    }

    //endregion

    //region Dialogs

    private fun msgclose(msg: String) {

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val dialog = AlertDialog.Builder(this)
                    dialog.setTitle("Impresion USB")
                    dialog.setMessage(msg)
                    dialog.setCancelable(false)
                    dialog.setNeutralButton("OK") { dialog, which ->
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed( { finish() }, 300)
                    }
                    dialog.show()
                } catch (ex: java.lang.Exception) {
                    //toast(ex?.message!!)
                }
            }, 100)

    }

    fun toast(msg: String) {

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                } catch (ex: java.lang.Exception) {  }
            }, 50)

    }

    fun toastlong(msg: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                } catch (ex: java.lang.Exception) { }
            }, 100)

    }

    //endregion

    //region Aux

    private fun loadImage(fname: String): Bitmap {
        val filePath = "${Environment.getExternalStorageDirectory()}/$fname"
        val bitmap = BitmapFactory.decodeFile(filePath)

        if (bitmap == null) {
            throw IllegalArgumentException("No se pudo cargar la imagen: $filePath")
        }

        return bitmap
    }

    //endregion

    //region Activity Events


    //endregion

}