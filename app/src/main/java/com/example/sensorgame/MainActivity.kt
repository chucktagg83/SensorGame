package com.example.sensorgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.sensorgame.ui.theme.SensorGameTheme
import kotlin.math.roundToInt
// class 1
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
// Class 2
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import kotlin.math.sqrt
import kotlin.random.Random


// fixed on screen size of ball, not state - it never changes after launch
private const val BALL_SIZE_DP = 60
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // gives us the theme's background color
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorGameScreen()
                }
            }

        }
    }
}

@Composable
fun SensorGameScreen() {
    // LocalDensity converts dp -> pixels, since sensor/drag math works in pixels
    val density = LocalDensity.current
    val ballPx = with(density) { BALL_SIZE_DP.dp.toPx() }

    // ballX/ballY = balls top-left corner, in pixels, inside the game area
    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }

    // areaWidth/HeightPx = the games area measured size, filled in once below
    var areaWidthPx by remember { mutableFloatStateOf(0f) }
    var areaHeightPx by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var gpsText by remember { mutableStateOf("") }

    // Gyroscope - remember{} means this only runs on first composition
    val context = LocalContext.current
    val sensorManager =
        remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyroscope = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val hasGyroscope = gyroscope != null

    // Live gyroscope readings - updated by the listener below, read in moveBallWithGyro()
    var gyroX by remember { mutableFloatStateOf(0f) }
    var gyroY by remember { mutableFloatStateOf(0f) }

    // pixels the ball moves per gyro unit per frame
    val baseSpeed = 5f

    // Accelerometer
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)}
    var lastShakeTime by remember { mutableStateOf(0L) }
    val shakeThreshold = 12f // m/s above gravity to count as a shake
    val shakeCooldown = 1500L // minimum ms between shake events

    // Ball color cycling
    var colorIndex by remember { mutableIntStateOf(0)}
    val ballColors = remember {
        listOf(
            Color.Blue,
            Color.Red,
            Color.Green,
            Color.Magenta,
            Color.Yellow
        )
    }

    // Stars - a list of positions, not a list of View objects
    val starPx = with(density) {32.dp.toPx() }
    val starCount = 5
    val stars = remember { mutableStateListOf<Offset>() }

    fun moveBallWithGyro() {
        if (!hasGyroscope) return
        // Assignment 1
        // 1. Update ballX based on the horizontal gyroscope reading and baseSpeed
        // 2. Update ballY based on the vertical gyroscope reading and baseSpeed
        // 3. Keep the ball on screen

        // Move ball horizontally
        ballX += gyroX * baseSpeed

        // Move ball vertically
        ballY += gyroY * baseSpeed

        // Keep the ball inside the game area
        ballX = ballX.coerceIn(
            0f, (areaWidthPx - ballPx).coerceAtLeast(0f)
        )
        ballY = ballY.coerceIn(0f, (areaHeightPx - ballPx).coerceAtLeast(0f))
    }

    fun onShakeDetected() {
        // Assignment 2
        // 1. Move to the next color in the list, wrapping back to the start the last one
        // 2. Give the player some feedback that ashake was detected
        colorIndex = (colorIndex + 1) % ballColors.size

        Toast.makeText(
            context,
            "Shake detected",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun spawnStars() {
        stars.clear()
        val bottomMarginPx = with(density) { 120.dp.toPx() }
        val safeHeight = (areaHeightPx - bottomMarginPx).coerceAtLeast(starPx * 2)
        repeat(starCount) {
            val x = Random.nextFloat() * (areaWidthPx - starPx)
            val y = Random.nextFloat() * (safeHeight - starPx)
            stars.add(Offset(x, y))
        }
    }

    fun checkStarCollisions() {
        val ballRight = ballX + ballPx
        val ballBottom = ballY + ballPx
        val collected = mutableListOf<Offset>()
        for (star in stars) {
            if (ballX < star.x + starPx && ballRight > star.x &&
                ballY < star.y + starPx && ballBottom > star.y) {
                collected.add(star)
            }
        }
        for (star in collected) {
            stars.remove(star)
            score += 10
        }
        if (stars.isEmpty()) spawnStars()
    }

    // Registers the gyroscope listener while the screen is visible, unregisters when it
    // isn't.  DisposableEffect + LifecycleEventObserver is the Compose equivalent of the
    // onResume() / onPause() / onDestroy() trio in a Views Activity.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Assignment 2
                // 1. Check which sensor this event came from
                // 2. If it's the gyroscope, store its readings so moveBallWithGyro() can use them
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // Store the X-axis reading
                    gyroX = event.values[0]
                    // Store the Y-axis reading
                    gyroY = event.values[1]
                }
                // Sensor.TYPE_ACCELEROMETER -> {
                    // Assignment 2
                    // 1. Read the three acceleration axes from this event
                    // 2. Work out how much force is being applied beyond gravity
                    // 3. If that force is large enough, and the cooldown has passed, react to the shake
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // magnitude of total acceleration minus constant gravity
                val net = sqrt(ax * ax + ay + ay + az * az) - SensorManager.GRAVITY_EARTH

                val now = android.os.SystemClock.elapsedRealtime()

                if (net > shakeThreshold &&
                    now - lastShakeTime >= shakeCooldown
                ) {
                    lastShakeTime = now
                    onShakeDetected()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    gyroscope?.let {
                        sensorManager.registerListener(
                            listener,
                            it,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }

                Lifecycle.Event.ON_PAUSE ->
                    sensorManager.unregisterListener(listener)

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasGyroscope) Toast.makeText(
            context,
            "No gyroscope - use touch controls",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(33L)
            moveBallWithGyro()
            checkStarCollisions()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (areaWidthPx == 0f) {
                    areaWidthPx = coords.size.width.toFloat()
                    areaHeightPx = coords.size.height.toFloat()

                    // Center the ball horizontally
                    ballX = (areaWidthPx - ballPx) / 2f

                    // Center the ball vertically
                    ballY = (areaHeightPx - ballPx) / 2f
                    spawnStars()
                }
            }
            .pointerInput(Unit) {
                // Touch to drag the ball in the emulator
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    // Horizontal movement
                    ballX = (ballX + dragAmount.x)
                        .coerceIn(0f, (areaWidthPx - ballPx).coerceAtLeast(0f))

                    // Vertical movement
                    ballY = (ballY + dragAmount.y)
                        .coerceIn(0f, (areaHeightPx - ballPx).coerceAtLeast(0f))
                }
            }
    ) {

        // GPS text - top-left corner
        Text(
            text = gpsText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
        // Score text - top-right corner
        Text(
            text = "Score: $score",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
        // The ball
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ballX.roundToInt(),
                        ballY.roundToInt()
                    )
                }
                .size(BALL_SIZE_DP.dp)
                .background(ballColors[colorIndex], CircleShape)
        )

        stars.forEach { starPos ->
            Image(
                painter = painterResource(R.drawable.star_shape),
                contentDescription = null,
                modifier = Modifier
                    .offset {IntOffset(starPos.x.roundToInt(), starPos.y.roundToInt()) }
                    .size(32.dp)
            )
        }

        // Shake and GPS buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {onShakeDetected() },
                enabled = true
            ) {
                Text("Shake")
            }

            Button(
                onClick = {},
                enabled = false
            ) {
                Text("GPS +10m")
            }
        }
    }
}