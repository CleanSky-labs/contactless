package io.cleansky.contactless

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// CleanSky blue
private val CleanSkyBlue = Color(0xFF0085FF)

@Composable
fun WelcomeScreen(
    onAccept: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanSkyBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // CleanSky white hexagon logo (for blue background)
            Image(
                painter = painterResource(id = R.drawable.ic_cleansky_white),
                contentDescription = "CleanSky",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = stringResource(R.string.welcome_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sovereignty statement
            Text(
                text = stringResource(R.string.welcome_sovereignty),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Single white card with all text content
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color.White,
                elevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Description
                    Text(
                        text = stringResource(R.string.welcome_desc),
                        fontSize = 16.sp,
                        color = Color.Black,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Warning title
                    Text(
                        text = stringResource(R.string.welcome_warning_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Warning description
                    Text(
                        text = stringResource(R.string.welcome_warning_desc),
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.8f),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Open source notice
                    Text(
                        text = stringResource(R.string.welcome_opensource),
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Accept button
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.welcome_start),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanSkyBlue
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
