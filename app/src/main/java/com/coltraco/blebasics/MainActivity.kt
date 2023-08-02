package com.coltraco.blebasics

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.coltraco.blebasics.ui.theme.BLEbasicsTheme
import com.coltraco.blebasics.util.hasRequiredRuntimePermissions

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.w("myApp", "Bluetooth accepted")
        } else {
            Log.w("myApp", "Bluetooth denied / other error")
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BLEbasicsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }

    // Check that bluetooth is enabled whenever we resume this activity
    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private val scanFilter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    ).build()

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setNumOfMatches(1)
        .build()

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result) {
                Log.i("ScanCallback", "Found BLE device! Name: ${device.name ?: "Unnamed"}, address: ${device.address}, Service UUIDs: ${scanRecord!!.serviceUuids}")
            }
        }
    }

    private var isScanning = false

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasRequiredRuntimePermissions()) {
            requestRelevantRuntimePermissions()
        }
        // at this point we can start the scan
        else {
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun onScanButtonPress() {
        if (isScanning) {
            stopBleScan()
        } else {
            startBleScan()
        }
    }

    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() {
        runOnUiThread {
            val onButtonClick = { _: DialogInterface, _: Int ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        ACCESS_FINE_LOCATION
                    ),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
            }

            val alertDialogBuilder = AlertDialog.Builder(this)

            with(alertDialogBuilder) {
                setTitle("Location permissions required")
                setMessage(
                    "Starting from Android M (6.0), the system requires apps to be granted " +
                            "location access in order to scan for BLE devices."
                )
                setPositiveButton("OK", DialogInterface.OnClickListener(function = onButtonClick))
                show()
            }
        }
    }

    private fun requestBluetoothPermissions() {

        runOnUiThread {
            val onButtonClick = { _: DialogInterface, _: Int ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        BLUETOOTH_SCAN,
                        BLUETOOTH_CONNECT
                    ),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
            }

            val alertDialogBuilder = AlertDialog.Builder(this)

            with(alertDialogBuilder) {
                setTitle("Bluetooth permissions required")
                setMessage(
                    "Starting from Android 12, the system requires apps to be granted " +
                            "Bluetooth access in order to scan for and connect to BLE devices."
                )
                setPositiveButton("OK", DialogInterface.OnClickListener(function = onButtonClick))
                show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                        // Note: The user will need to navigate to App Settings and manually grant
                        // permissions that were permanently denied
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario encountered when handling permissions
                        recreate()
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen(modifier: Modifier = Modifier) {
        Column(modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Coltraco RDDE BLE Scanning app",
                modifier = modifier
                    .padding(all = 5.dp)
            )
            Button(
                modifier = modifier
                    .padding(all = 10.dp),
                onClick = { onScanButtonPress() }) {
                Text("Initialise Scan")
            }
        }
    }



    @Preview
    @Composable
    fun PreviewHomeScreen(modifier: Modifier = Modifier) {
        Surface {
            HomeScreen()
        }
    }
}

