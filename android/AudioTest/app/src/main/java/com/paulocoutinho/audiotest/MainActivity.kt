package com.paulocoutinho.audiotest

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.paulocoutinho.audiotest.ui.theme.AudioTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AudioTestTheme {
                AudioDebugScreen(
                    defaultUrl = "https://streams.radiomast.io/ref-128k-mp3-stereo"
                )
            }
        }
    }
}

// ------------------- UI + Player ---------------------

sealed class AudioState {
    object Idle : AudioState()
    object Loading : AudioState()
    object Playing : AudioState()
    data class Error(val message: String?) : AudioState()
}

@Composable
fun AudioDebugScreen(defaultUrl: String) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    var inputUrl by remember { mutableStateOf(defaultUrl) }
    var audioState by remember { mutableStateOf<AudioState>(AudioState.Idle) }
    val logList = remember { mutableStateListOf<String>() }

    fun addLog(msg: String) {
        logList.add(msg)
        Log.d("AudioDebug", msg)
    }

    DisposableEffect(Unit) {
        addLog("Player created")

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        audioState = AudioState.Loading
                        addLog("STATE: BUFFERING...")
                    }

                    Player.STATE_READY -> {
                        audioState = AudioState.Playing
                        addLog("STATE: READY (playing)")
                        player.play()
                    }

                    Player.STATE_ENDED -> addLog("STATE: ENDED")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                audioState = AudioState.Error(error.message)
                val detailed = """
                    ❌ PLAYER ERROR
                    Code: ${error.errorCode}
                    Code Name: ${error.errorCodeName}
                    Message: ${error.message}
                    Cause: ${error.cause}
                    Stacktrace:
                    ${error.stackTraceToString()}
                """.trimIndent()
                addLog(detailed)
            }
        })

        onDispose {
            addLog("Releasing player...")
            player.release()
        }
    }

    fun startPlayback(url: String) {
        if (url.isBlank()) {
            addLog("⚠️ URL EMPTY")
            return
        }

        logList.clear()
        addLog("STARTING NEW PLAYBACK")
        addLog("URL: $url")
        audioState = AudioState.Loading

        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()

        } catch (e: Exception) {
            audioState = AudioState.Error(e.message)
            addLog("EXCEPTION: ${e.message}")
            addLog(e.stackTraceToString())
        }
    }

    fun stopPlayback() {
        addLog("STOP called")
        player.stop()
        player.clearMediaItems()
        audioState = AudioState.Idle
    }

    AudioDebugUI(
        audioState = audioState,
        logs = logList,
        inputUrl = inputUrl,
        onUrlChange = { inputUrl = it },
        onPlayClick = { startPlayback(inputUrl.trim()) },
        onStopClick = { stopPlayback() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDebugUI(
    audioState: AudioState,
    logs: List<String>,
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CenterAlignedTopAppBar(title = { Text("Audio Debug Player") }) }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            OutlinedTextField(
                value = inputUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Audio URL") }
            )

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier.weight(1f)
                ) { Text("PLAY") }

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = onStopClick,
                    modifier = Modifier.weight(1f)
                ) { Text("STOP") }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (audioState) {
                    AudioState.Idle -> "Status: Idle"
                    AudioState.Loading -> "Status: Loading..."
                    AudioState.Playing -> "Status: Playing"
                    is AudioState.Error -> "Status: Error ❌"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(12.dp))

            Text("Log:", style = MaterialTheme.typography.titleSmall)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAudioDebug() {
    AudioTestTheme {
        AudioDebugUI(
            AudioState.Idle,
            emptyList(),
            inputUrl = "",
            onUrlChange = {},
            onPlayClick = {},
            onStopClick = {}
        )
    }
}
