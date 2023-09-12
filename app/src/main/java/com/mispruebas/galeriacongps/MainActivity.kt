package com.mispruebas.galeriacongps



import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime
import androidx.exifinterface.media.ExifInterface.*


enum class Pantalla {// aca se agregan las pantallas
    CapturaFoto,
    MiniaturaFoto,
    Ubicacion
}

class AppVM : ViewModel() {//esto es el viewmodel para la ubicacion
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    var permisoUbicacionOk: () -> Unit = {}
}

class AppCameraVM : ViewModel() {//esto es el viewmodel para la camara
    val pantallaPrincipal = mutableStateOf(Pantalla.CapturaFoto)//aca se pone la pantalla principal
    val ubicacionFoto = mutableStateOf<Location?>(null)//esto es para la ubicacion
    var onPermisoCamara: () -> Unit = {}//esto es para la camara
    var onPermisoUbicacion: () -> Unit = {}//esto es para la ubicacion
    val mostrarMiniatura = mutableStateOf(false)//esto es para la camara
}

class FormularioVM : ViewModel() {//esto es el viewmodel para el formulario
    val nombre = mutableStateOf("")//con esto se crea el campo nombre
    val fotos = mutableStateListOf<Uri>() // Cambio a lista de Uri
}

fun nombreSegunFecha(): String = LocalDateTime.now()// esta funcion es para ponerle el nombre a la foto
    .toString()
    .replace(Regex("[T:.-]"), "")
    .substring(0, 14)

fun archivoPrivado(contexto: Context): File = File(//esta funcion es para crear el archivo de la foto
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${nombreSegunFecha()}.jpg"
)

fun imagenUriToBitmap(uri: Uri, contexto: Context): Bitmap =//esta funcion es para convertir la foto en bitmap
    BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri))

class MainActivity : ComponentActivity() {
    val camaraVm: AppCameraVM by viewModels()
    val formularioVM: FormularioVM by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val lanzarPermisosCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            camaraVm.onPermisoCamara()
        }
    }

    val lanzarPermisosUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            camaraVm.onPermisoUbicacion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        setContent {
            AppCamera(lanzarPermisosCamara, lanzarPermisosUbicacion, cameraController)
        }
    }
}

@Composable
fun AppCamera(
    lanzarPermisosCamara: ActivityResultLauncher<String>,
    lanzarPermisosUbicacion: ActivityResultLauncher<String>,
    cameraController: LifecycleCameraController
) {
    val appCameraVM: AppCameraVM = viewModel()
    when (appCameraVM.pantallaPrincipal.value) {
        Pantalla.CapturaFoto -> pantallaCapturaFoto(lanzarPermisosCamara, cameraController, appCameraVM)
        Pantalla.MiniaturaFoto -> pantallaMiniaturaFoto()
        Pantalla.Ubicacion -> pantallaUbicacion()
    }
}

@Composable
fun pantallaCapturaFoto(
    lanzarPermisosCamara: ActivityResultLauncher<String>,
    cameraController: LifecycleCameraController,
    appCameraVM: AppCameraVM
) {
    val contexto = LocalContext.current
    val formularioVM: FormularioVM = viewModel()
    lanzarPermisosCamara.launch(android.Manifest.permission.CAMERA)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = {
                tomarFoto(
                    cameraController,
                    archivoPrivado(contexto),
                    contexto
                ) {
                    formularioVM.fotos.add(it) // Agregar la Uri a la lista de fotos
                    appCameraVM.pantallaPrincipal.value = Pantalla.MiniaturaFoto
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Tomar foto")
        }
        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.Ubicacion
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Ver Ubicación")
        }
    }
}

fun tomarFoto(
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onFotoTomada: (uri: Uri) -> Unit
) {
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    onFotoTomada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Error", exception.message.toString())
            }
        }
    )
}

@Composable
fun pantallaMiniaturaFoto() {
    val appCameraVM: AppCameraVM = viewModel()
    val formularioVM: FormularioVM = viewModel()
    val contexto = LocalContext.current
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        formularioVM.fotos.forEach { uri ->
            val imageBitmap = imagenUriToBitmap(uri, contexto)
            val rotatedImageBitmap = rotateBitmap(imageBitmap, uri, contexto)
            val rotatedImage = rotatedImageBitmap.asImageBitmap()
            // Aplicar rotación aquí

            Image(
                bitmap = rotatedImage,
                contentDescription = "Imagen capturada",
                modifier = Modifier
                    .aspectRatio(1f)
                    .border(1.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                    .padding(4.dp)
            )
        }

        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.Ubicacion
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = "Ver Ubicación")
        }

        if (appCameraVM.mostrarMiniatura.value) {
            Button(
                onClick = {
                    appCameraVM.mostrarMiniatura.value = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Volver")
            }
        }
    }
}

fun rotateBitmap(bitmap: Bitmap, uri: Uri, context: Context): Bitmap {
    val rotation = getBitmapRotation(uri, context)
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

fun getBitmapRotation(uri: Uri, context: Context): Int {
    val exifInterface = androidx.exifinterface.media.ExifInterface(uri.path!!)
    return when (exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

@Composable
fun pantallaUbicacion() {
    val appCameraVM: AppCameraVM = viewModel()
    val appVM: AppVM = viewModel()
    val contexto = LocalContext.current
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AndroidView(
            factory = {
                MapView(it).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
                    controller.setZoom(15.0)
                }
            }, update = {
                it.overlays.removeIf { true }
                it.invalidate()

                appCameraVM.ubicacionFoto.value?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    it.controller.animateTo(geoPoint)

                    val marcador = Marker(it)
                    marcador.position = geoPoint
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    it.overlays.add(marcador)
                }
            }
        )
        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.MiniaturaFoto
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(text = "Volver a Foto")
        }
    }
}



