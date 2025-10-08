package com.spam.buzzergame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.spam.buzzergame.ui.theme.BuzzerGameTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController




import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// (Keep your Team data class and availableTeams list here)
data class Team(val id: String, val name: String, val color: Color)
val availableTeams = listOf(
    Team("red", "Red Team", Color(0xFFE53935)),
    Team("blue", "Blue Team", Color(0xFF1E88E5)),
    Team("green", "Green Team", Color(0xFF43A047)),
    Team("yellow", "Yellow Team", Color(0xFFFDD835))
)
val maxCodeL = 6 // Room code length

// (Keep your generateRandomRoomCode function here)
fun generateRandomRoomCode(): String {
    val allowedChars = "ABCDEFGHIJKLMNPQRSTUVWXYZ23456789" // No 0, O, 1
    return (1..6).map { allowedChars.random() }.joinToString("")
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuzzerGameTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.statusBarsPadding()){
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val buzzerViewModel: BuzzerViewModel = viewModel() // Get a single ViewModel instance for the whole app

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(navController = navController, viewModel = buzzerViewModel)
        }
        composable("team_selection/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: "ERROR"
            buzzerViewModel.joinRoom(roomCode) // << TELL VIEWMODEL TO START LISTENING
            TeamSelectionScreen(navController = navController, roomCode = roomCode)
        }
        composable("buzzer/{roomCode}/{teamId}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: "ERROR"
            val teamId = backStackEntry.arguments?.getString("teamId") ?: "red"
            val selectedTeam = availableTeams.find { it.id == teamId } ?: availableTeams[0]
            BuzzerScreen(roomCode = roomCode, myTeam = selectedTeam, viewModel = buzzerViewModel)
        }
        composable("teacher/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: "ERROR"
            // No need to call createRoom again, it's done in WelcomeScreen
            TeacherScreen(roomCode = roomCode, viewModel = buzzerViewModel)
        }
    }
}

@Composable
fun WelcomeScreen(navController: NavController, viewModel: BuzzerViewModel) {
    var roomCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    val validationStatus by viewModel.validationStatus.collectAsState() // Observe the validation state

    // This effect runs when the validationStatus changes
    LaunchedEffect(validationStatus) {
        if (validationStatus == ValidationStatus.SUCCESS) {
            // If validation was successful, navigate and then reset the state
            navController.navigate("team_selection/$roomCode")
            viewModel.resetValidationStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Classroom Buzzer",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = roomCode,
            onValueChange = {
                if (it.length <= maxCodeL) {
                    roomCode = it.uppercase()
                    // If user starts typing again, reset the error message
                    if (validationStatus == ValidationStatus.FAILURE) {
                        viewModel.resetValidationStatus()
                    }
                }
            },
            label = { Text("Enter Room Code") },
            singleLine = true,
            isError = (validationStatus == ValidationStatus.FAILURE), // Show error state on TextField
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        // Show an error message if validation failed
        if (validationStatus == ValidationStatus.FAILURE) {
            Text(
                "Room not found. Please check the code.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                // Instead of navigating, we now trigger the validation
                if (roomCode.isNotBlank() && roomCode.length == maxCodeL) {
                    viewModel.validateRoomCode(roomCode)
                }
            },
            // Disable the button while checking
            enabled = (validationStatus != ValidationStatus.LOADING),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) {
            if (validationStatus == ValidationStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Join Room")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("OR")
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val newRoomCode = generateRandomRoomCode()
                viewModel.createRoom(context, newRoomCode)
                navController.navigate("teacher/$newRoomCode")
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) { Text("Create Room (Teacher)") }
    }
}

// TeamSelectionScreen does not need the ViewModel, so it remains unchanged
@Composable
fun TeamSelectionScreen(navController: NavController, roomCode: String) {
    // ... your existing TeamSelectionScreen code ...
    // ... I am omitting it here for brevity but you should KEEP it ...
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Choose Your Team!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Room Code: $roomCode",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TeamButton(
                    team = availableTeams[0],
                    navController = navController,
                    roomCode = roomCode
                )
                TeamButton(
                    team = availableTeams[1],
                    navController = navController,
                    roomCode = roomCode
                )
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TeamButton(
                    team = availableTeams[2],
                    navController = navController,
                    roomCode = roomCode
                )
                TeamButton(
                    team = availableTeams[3],
                    navController = navController,
                    roomCode = roomCode
                )
            }
        }
    }
}

@Composable
fun TeamButton(team: Team, navController: NavController, roomCode: String) {
    // ... your existing TeamButton code ...
    Button(
        onClick = { navController.navigate("buzzer/$roomCode/${team.id}") },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = team.color),
        modifier = Modifier
            .size(150.dp)
            .padding(8.dp)
    ) {
        Text(
            text = team.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
fun BuzzerScreen(roomCode: String, myTeam: Team, viewModel: BuzzerViewModel) {
    val roomState by viewModel.roomState.collectAsState() // << GET LIVE DATA FROM VIEWMODEL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Room: $roomCode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your Team: ${myTeam.name}",
                style = MaterialTheme.typography.titleLarge,
                color = myTeam.color,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (roomState.isBuzzerActive) {
                Text(
                    "Buzzer is Active!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    "${roomState.buzzedInTeamName}'s Turn!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                // Timer logic can be added here later
            }
        }
        Button(
            onClick = { viewModel.buzzIn(roomCode, myTeam) }, // << CALL BUZZ IN ON CLICK
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape),
            elevation = ButtonDefaults.buttonElevation(8.dp),
            enabled = roomState.isBuzzerActive // << BUTTON IS ENABLED BASED ON LIVE DATA
        ) {
            Text("BUZZ!", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(50.dp)) // Spacer for layout balance
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(roomCode: String, viewModel: BuzzerViewModel) { // <-- PARAMETER REMOVED HERE
    val roomState by viewModel.roomState.collectAsState()
    val context = LocalContext.current
    val isAdVisible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            if (isAdVisible) {
                AdmobBanner(modifier = Modifier.statusBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            // All the content inside here remains exactly the same.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Room Code", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(roomCode, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("Share this code with your students", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("STATUS", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                if (roomState.buzzedInTeamName != null) {
                    Text(
                        text = "${roomState.buzzedInTeamName}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text("has buzzed in!")
                } else {
                    Text(
                        text = "Buzzer is Ready",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Waiting for a team to buzz in...")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { viewModel.resetBuzzer(roomCode) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Buzzer / Next Question", fontSize = 18.sp)
                }
            }

        }
    }
}
@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    // Create a remembered AdView instance
    val adView = remember { AdView(context) }

    // Use a DisposableEffect to tie the AdView's lifecycle to the composable's lifecycle
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the composable is disposed, remove the observer and destroy the ad.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            adView.apply {
                // Determine the adaptive banner size.
                val screenWidthDp = configuration.screenWidthDp.toFloat()
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    screenWidthDp.toInt()
                )
                setAdSize(adSize)

                // IMPORTANT: Use the TEST Ad Unit ID for development.
                // Replace with your REAL Ad Unit ID before publishing.
                adUnitId = "ca-app-pub-3940256099942544/6300978111"

                // Create an ad request and load the ad.
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}