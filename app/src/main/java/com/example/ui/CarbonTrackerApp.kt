package com.example.ui

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CarbonActivity
import com.example.viewmodel.CarbonViewModel
import com.example.viewmodel.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CarbonTrackerApp(viewModel: CarbonViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is com.example.viewmodel.AuthState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is com.example.viewmodel.AuthState.Authenticated -> {
            AuthenticatedApp(viewModel = viewModel)
        }
        else -> {
            AuthScreen(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthenticatedApp(viewModel: CarbonViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Log Activity, 2: AI Advisor, 3: Settings
    val activities by viewModel.allActivities.collectAsStateWithLifecycle()
    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(if (currentTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(if (currentTab == 1) Icons.Filled.AddCircle else Icons.Outlined.AddCircle, contentDescription = "Track") },
                    label = { Text("Track", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("nav_log")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(if (currentTab == 2) Icons.Filled.Psychology else Icons.Outlined.Psychology, contentDescription = "AI Advisor") },
                    label = { Text("AI Advisor", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("nav_ai")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(if (currentTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                    (slideIntoContainer(direction, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutOfContainer(direction, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = tween(220)))
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> DashboardScreen(viewModel = viewModel, activities = activities, dailyTarget = dailyTarget, userName = userName, onNavigateToLog = { currentTab = 1 })
                    1 -> LogActivityScreen(viewModel = viewModel, onNavigateToHome = { currentTab = 0 })
                    2 -> AiAdvisorScreen(viewModel = viewModel)
                    3 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// --- TAB 0: DASHBOARD ---
@Composable
fun DashboardScreen(
    viewModel: CarbonViewModel,
    activities: List<CarbonActivity>,
    dailyTarget: Double,
    userName: String,
    onNavigateToLog: () -> Unit
) {
    val scrollState = rememberScrollState()
    var dashboardSubTab by remember { mutableStateOf(0) } // 0: Daily Dashboard, 1: Reduction Hub

    // Calculate aggregated metrics
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfToday = calendar.timeInMillis

    val rawTodayEmissions = activities.asSequence()
        .filter { it.timestamp >= startOfToday && it.co2Emitted > 0 }
        .sumOf { it.co2Emitted }

    val rawTodaySavings = activities.asSequence()
        .filter { it.timestamp >= startOfToday && it.co2Emitted < 0 }
        .sumOf { -it.co2Emitted }

    val netTodayEmissions = rawTodayEmissions - rawTodaySavings

    // Category breakdown overall (or past 30 days)
    val categoryTotals = remember(activities) {
        val breakdown = mutableMapOf<String, Double>()
        activities.forEach {
            breakdown[it.category] = (breakdown[it.category] ?: 0.0) + it.co2Emitted
        }
        breakdown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CarbonTrace Elegant Top Header Bar (Natural Tones Custom Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WELCOME BACK, $userName".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CarbonTrace",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Theme Toggle & Circular Person Avatar Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isDarkFlow by viewModel.isDarkMode.collectAsStateWithLifecycle()
                IconButton(
                    onClick = { viewModel.toggleDarkMode() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isDarkFlow) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Toggle Dark Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Custom Pill Sub-Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("⚡ Live Meter", "🌿 Reduction Hub").forEachIndexed { index, title ->
                val isSelected = dashboardSubTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { dashboardSubTab = index }
                        .padding(vertical = 10.dp)
                        .testTag("sub_tab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (dashboardSubTab == 1) {
            ReductionHubContent(
                viewModel = viewModel,
                activities = activities,
                onNavigateToLog = onNavigateToLog
            )
        } else {
            // Circular budget ring widget
            Spacer(modifier = Modifier.height(8.dp))
            BudgetRingWidget(
                netEmissions = netTodayEmissions,
                dailyBudget = dailyTarget,
                todaySavings = rawTodaySavings,
                todayGrossEmissions = rawTodayEmissions
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToLog,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("btn_dashboard_log"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Activity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.generateFeedbackPlan(silent = false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("btn_get_ai_insights"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Review Plan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chart or Category Breakdown cards
            Text(
                text = "Total Category Breakdown (all time)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (activities.isEmpty()) {
                EmptyLogsPlaceholder(onNavigateToLog)
            } else {
                CategoryProgressCards(categoryTotals)
                Spacer(modifier = Modifier.height(24.dp))

                // Recent Logs lists header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { viewModel.clearAllData() }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                RecentLogsList(activities = activities, onDelete = { id -> viewModel.deleteActivity(id) })
            }
        }
    }
}

@Composable
fun ReductionHubContent(
    viewModel: CarbonViewModel,
    activities: List<CarbonActivity>,
    onNavigateToLog: () -> Unit
) {
    val context = LocalContext.current

    // Read details from general StateFlow in ViewModel
    val baselineTransport by viewModel.baselineTransportMiles.collectAsStateWithLifecycle()
    val baselineDiet by viewModel.baselineDietMeatMeals.collectAsStateWithLifecycle()
    val baselineEnergy by viewModel.baselineEnergyKwh.collectAsStateWithLifecycle()
    val reductionGoalPercent by viewModel.reductionGoalPercent.collectAsStateWithLifecycle()

    // Calculate baseline weekly footprint:
    // Transport: weekly miles * 0.40 kg co2
    // Diet: weekly meat-based meals * 4.0 kg co2
    // Energy: weekly kwh * 0.38 kg co2
    val transportBaselineCo2 = baselineTransport * 0.40
    val dietBaselineCo2 = baselineDiet * 4.0
    val energyBaselineCo2 = baselineEnergy * 0.38
    val totalWeeklyBaseline = transportBaselineCo2 + dietBaselineCo2 + energyBaselineCo2

    // Real-time weekly savings targets
    val targetReductionCo2 = totalWeeklyBaseline * (reductionGoalPercent / 100.0)
    val allowableWeeklyTargetCo2 = totalWeeklyBaseline - targetReductionCo2

    // Accumulative logged footprint reductions (entries with negative co2Emitted in history)
    val totalActualSavings = remember(activities) {
        activities.asSequence()
            .filter { it.co2Emitted < 0 }
            .sumOf { -it.co2Emitted }
    }

    // Goal progress percentage matching actual savings to reduction goal target
    val progressPercent = if (targetReductionCo2 > 0) {
        (totalActualSavings / targetReductionCo2).coerceAtMost(1.0).toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CARD 1: OVERALL GOAL & IMPACT METER (THE PROGRESS TRACKER)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "CARBON REDUCTION TARGET PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${String.format("%.1f", totalActualSavings)} kg Saved",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Weekly Target Reduction Goal: ${String.format("%.1f", targetReductionCo2)} kg/wk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    // Badge containing percent offset complete
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(progressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Smooth Linear progress indicator for goal
                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("reduction_progress_bar"),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Aggressive Goal: ${reductionGoalPercent.toInt()}% Reduction",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Badge: " + when {
                            progressPercent >= 1.0f -> "Goal Met! 🏆"
                            progressPercent >= 0.5f -> "Halfway Hero 🌿"
                            progressPercent >= 0.1f -> "Sprout Catalyst 🌱"
                            else -> "Pioneer Pathfinder 🗺️"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MILIEU OF MILESTONES
        Text(
            text = "Achievement Certification Levels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val achievementsList = listOf(
                Triple("Carbon Catalyst", 10.0, Icons.Filled.Star),
                Triple("Eco Warrior", 50.0, Icons.Filled.Spa),
                Triple("Green Guardian", 100.0, Icons.Filled.Terrain)
            )

            achievementsList.forEach { (name, req, iconVar) ->
                val isUnlocked = totalActualSavings >= req
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            Toast.makeText(context, "$name: Requires $req kg overall footprint offsets to certify. You have logged ${String.format("%.1f", totalActualSavings)} kg.", Toast.LENGTH_LONG).show()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = iconVar,
                            contentDescription = name,
                            tint = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${req.toInt()} kg target",
                            fontSize = 9.sp,
                            color = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CARD 2: BASELINE ESTIMATOR (THE CARBON TRACKER CALCULATOR CONFIG)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Carbon Footprint Estimator / Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Weekly general baseline: ${String.format("%.1f", totalWeeklyBaseline)} kg CO₂e",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "Footprint baseline configuration",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // TRANSPORT SLIDER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Weekly Commuter Commute:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("${baselineTransport.toInt()} miles", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = baselineTransport.toFloat(),
                    onValueChange = { viewModel.updateBaselineTransportMiles(it.toDouble()) },
                    valueRange = 0f..250f,
                    steps = 25,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // DIET SLIDER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Meat/Dairy Main Meals weekly:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("${baselineDiet.toInt()} meals", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = baselineDiet.toFloat(),
                    onValueChange = { viewModel.updateBaselineDietMeatMeals(it.toDouble()) },
                    valueRange = 0f..21f,
                    steps = 21,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ELECTRICITY SLIDER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Home Grid Electricity consumption:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("${baselineEnergy.toInt()} kWh", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = baselineEnergy.toFloat(),
                    onValueChange = { viewModel.updateBaselineEnergyKwh(it.toDouble()) },
                    valueRange = 0f..150f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // TARGET REDUCTION GOAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize Reduction Target:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${reductionGoalPercent.toInt()}% Reduction",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Slider(
                    value = reductionGoalPercent.toFloat(),
                    onValueChange = { viewModel.updateReductionGoalPercent(it.toDouble()) },
                    valueRange = 5f..60f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Meeting this targets reduces your weekly emissions by ${String.format("%.1f", targetReductionCo2)} kg CO₂e to an allowable target of ${String.format("%.1f", allowableWeeklyTargetCo2)} kg CO₂e.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CARD 3: LOG ACTIVE MINIMIZING REDUCTION CHALLENGES (QUICK LOGGER STATS CHECK)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Track High-Impact Reducing Actions",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Complete real-world carbon saving actions and tap below to log offsets instantly to your progress tracker!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                val reductionActionTrackers = listOf(
                    FastActionItem("Choose Vegan/Veggie Meal", "Vegan Meal", 1.0, "Diet", -5.4, "Eliminates ~5.4 kg CO2e meat/dairy emissions"),
                    FastActionItem("Bike or Commute on Foot", "Active (Bike/Walk)", 5.0, "Transport", -4.5, "Eliminates ~4.5 kg transport gasoline CO2e"),
                    FastActionItem("Plant a Tree or Sapling", "Planted sapling / tree", 1.0, "Consumption", -40.0, "Provides a direct carbon sequestration of -40 kg CO2e"),
                    FastActionItem("Recycle bottles, containers, cans", "Recycling (bottle/can)", 5.0, "Consumption", -1.5, "Avoids ~1.5 kg CO2e manufacturing emissions"),
                    FastActionItem("Turn off high standby draw", "Refused Standby Idle", 1.0, "Energy", -0.8, "Neutralizes ~0.8 kg vampire energy leaks")
                )

                reductionActionTrackers.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = action.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.logActivity(
                                    category = action.category,
                                    subCategory = action.subCat,
                                    value = action.quantity,
                                    unit = "acts",
                                    co2Emitted = action.co2Savings,
                                    notes = "Tracked direct reduction in Carbon Reduction Hub"
                                )
                                Toast.makeText(context, "${action.title} successfully tracked! Savings registered.", Toast.LENGTH_SHORT).show()
                                viewModel.speak("Sensational decision! You registered ${action.title} and offset ${-action.co2Savings} kg of carbon. Keep up this magnificent momentum!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Log Act", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (action != reductionActionTrackers.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

data class FastActionItem(
    val title: String,
    val subCat: String,
    val quantity: Double,
    val category: String,
    val co2Savings: Double,
    val description: String
)

@Composable
fun BudgetRingWidget(
    netEmissions: Double,
    dailyBudget: Double,
    todaySavings: Double,
    todayGrossEmissions: Double
) {
    val percentage = if (dailyBudget > 0) (netEmissions.coerceAtLeast(0.0) / dailyBudget).toFloat() else 0f
    val isOverBudget = netEmissions > dailyBudget

    val progressBrush = Brush.sweepGradient(
        if (isOverBudget) {
            listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFFE53935))
        } else {
            listOf(Color(0xFF81C784), Color(0xFFAED581), Color(0xFF81C784))
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A4D39)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DAILY IMPACT",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFDCE5D5),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(174.dp)
            ) {
                // Background circle
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        style = Stroke(width = 14.dp.toPx())
                    )
                }

                // Dynamic progress circle
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawArc(
                        brush = progressBrush,
                        startAngle = -90f,
                        sweepAngle = (percentage * 360f).coerceAtMost(360f),
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.1f", netEmissions),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "kg CO₂e",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDCE5D5),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Split metrics info (Gross emissions vs savings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF8A65)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gross Core", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDCE5D5))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${String.format("%.1f", todayGrossEmissions)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(modifier = Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF81C784)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eco Offsets", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDCE5D5))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${String.format("%.1f", todaySavings)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                }
            }
        }
    }
}

@Composable
fun EmptyLogsPlaceholder(onNavigateToLog: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No logs tracked yet!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Unlock daily targets, custom offsets, and personalized AI tips by logging your first transport trip or meal.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateToLog) {
                Text("Log Activity")
            }
        }
    }
}

@Composable
fun CategoryProgressCards(categoryTotals: Map<String, Double>) {
    val categories = listOf("Transport", "Diet", "Energy", "Consumption")
    val categoryColors = mapOf(
        "Transport" to Color(0xFFFB8C00),
        "Diet" to Color(0xFF8D6E63),
        "Energy" to Color(0xFFFBC02D),
        "Consumption" to Color(0xFF0288D1)
    )
    val categoryIcons = mapOf(
        "Transport" to Icons.Filled.DirectionsCar,
        "Diet" to Icons.Filled.Restaurant,
        "Energy" to Icons.Filled.Bolt,
        "Consumption" to Icons.Filled.ShoppingBag
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE1E4DC))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            categories.forEach { category ->
                val ems = categoryTotals[category] ?: 0.0
                val displayEms = if (ems < 0) 0.0 else ems // Focus on positive footprint for breakdown progress
                val fraction = if (ems > 0) (displayEms / (categoryTotals.values.filter { it > 0 }.sum().coerceAtLeast(1.0))).toFloat() else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = categoryIcons[category] ?: Icons.Filled.Eco,
                        contentDescription = null,
                        tint = categoryColors[category] ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(category, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format("%.1f", ems)} kg CO₂e", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = categoryColors[category] ?: MaterialTheme.colorScheme.primary,
                            trackColor = Color.LightGray.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentLogsList(activities: List<CarbonActivity>, onDelete: (Int) -> Unit) {
    activities.take(7).forEach { activity ->
        val dateString = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(activity.timestamp))
        val isSaving = activity.co2Emitted < 0

        val categoryIcon = when (activity.category) {
            "Transport" -> Icons.Filled.DirectionsCar
            "Diet" -> Icons.Filled.Restaurant
            "Energy" -> Icons.Filled.Bolt
            else -> Icons.Filled.Eco
        }

        val categoryColor = when (activity.category) {
            "Transport" -> Color(0xFFFB8C00)
            "Diet" -> Color(0xFF8D6E63)
            "Energy" -> Color(0xFFFBC02D)
            else -> Color(0xFF0288D1)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.subCategory,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$dateString • ${activity.value} ${activity.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isSaving) "${String.format("%.1f", activity.co2Emitted)} kg" else "+${String.format("%.1f", activity.co2Emitted)} kg",
                        color = if (isSaving) Color(0xFF0288D1) else Color(0xFFD84315),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { onDelete(activity.id) },
                        modifier = Modifier.size(24.dp).testTag("delete_${activity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Log",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


// --- TAB 1: LOG ACTIVITY ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogActivityScreen(viewModel: CarbonViewModel, onNavigateToHome: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf("Transport") }
    var selectedSubCategory by remember { mutableStateOf("Petrol Car") }
    var quantityInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    val isSuccessLogged = remember { mutableStateOf(false) }

    val categories = listOf("Transport", "Diet", "Energy", "Consumption")

    val subCategories = when (selectedCategory) {
        "Transport" -> listOf("Petrol Car", "Electric Car", "Public Bus/Train", "Active (Bike/Walk)", "Short Flight", "Long Flight")
        "Diet" -> listOf("Beef/Lamb Meal", "Chicken/Pork Meal", "Fish/Seafood Meal", "Vegetarian Meal", "Vegan Meal", "Avoided Food Waste")
        "Energy" -> listOf("Grid Electricity", "Solar Generated Power", "Natural Gas", "Water Usage")
        else -> listOf("New Apparel Item", "New Electronics Item", "Packaging Waste", "Planted sapling / tree", "Recycling (bottle/can)", "Refused Standby Idle")
    }

    // Set standard unit based on subcategory
    val unit = when (selectedCategory) {
        "Transport" -> "miles"
        "Diet" -> "meals"
        "Energy" -> when (selectedSubCategory) {
            "Grid Electricity", "Solar Generated Power" -> "kWh"
            "Natural Gas" -> "therms"
            else -> "gallons"
        }
        else -> when (selectedSubCategory) {
            "Planted sapling / tree", "Switched Standby" -> "instances"
            "Recycling (bottle/can)" -> "items"
            else -> "items"
        }
    }

    // Calculate preview emissions in real-time
    val calculatedFootprint = remember(selectedSubCategory, quantityInput) {
        val qty = quantityInput.toDoubleOrNull() ?: 0.0
        when (selectedSubCategory) {
            "Petrol Car" -> qty * 0.40
            "Electric Car" -> qty * 0.12
            "Public Bus/Train" -> qty * 0.08
            "Active (Bike/Walk)" -> 0.0
            "Short Flight" -> qty * 0.30
            "Long Flight" -> qty * 0.22

            "Beef/Lamb Meal" -> qty * 6.0
            "Chicken/Pork Meal" -> qty * 2.5
            "Fish/Seafood Meal" -> qty * 1.8
            "Vegetarian Meal" -> qty * 1.2
            "Vegan Meal" -> qty * 0.6
            "Avoided Food Waste" -> qty * -0.3

            "Grid Electricity" -> qty * 0.38
            "Solar Generated Power" -> qty * -0.38
            "Natural Gas" -> qty * 5.3
            "Water Usage" -> qty * 0.005

            "New Apparel Item" -> qty * 12.0
            "New Electronics Item" -> qty * 80.0
            "Packaging Waste" -> qty * 0.8
            "Planted sapling / tree" -> qty * -40.0
            "Recycling (bottle/can)" -> qty * -0.3
            "Switched Standby" -> qty * -0.8
            else -> qty * 1.0
        }
    }

    // Auto-update selected subcategory to first of category when category changes
    LaunchedEffect(selectedCategory) {
        selectedSubCategory = subCategories.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Track Footprint Log",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select a category and log your daily metrics. Green calculations indicate an active reduction/offset!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Segmented selector cards
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { Text(category, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_cat_$category")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Subcategory Selection Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Subcategory Selection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable row of chips for subcategories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subCategories.forEach { sub ->
                        val isSelected = selectedSubCategory == sub
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { selectedSubCategory = sub },
                            label = { Text(sub) },
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("chip_sub_$sub")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input Fields (Value and Notes)
        OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it },
            label = { Text("Log Amount ($unit)") },
            placeholder = { Text("e.g. 15") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_qty"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = { Text("Notes (optional)") },
            placeholder = { Text("e.g. Commute to office, vegetarian lunch challenge") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_notes"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Emission Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (calculatedFootprint < 0) {
                    Color(0xFF0288D1).copy(alpha = 0.1f)
                } else if (calculatedFootprint == 0.0) {
                    Color.Gray.copy(alpha = 0.1f)
                } else {
                    Color(0xFFD84315).copy(alpha = 0.1f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val label = if (calculatedFootprint < 0) "Est. Carbon Offset" else "Est. Carbon Footprint"
                val valueColor = if (calculatedFootprint < 0) Color(0xFF0288D1) else if (calculatedFootprint == 0.0) Color.DarkGray else Color(0xFFD84315)

                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${String.format("%.2f", calculatedFootprint)} kg CO₂e",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val valDouble = quantityInput.toDoubleOrNull() ?: 0.0
                if (valDouble > 0) {
                    viewModel.logActivity(
                        category = selectedCategory,
                        subCategory = selectedSubCategory,
                        value = valDouble,
                        unit = unit,
                        co2Emitted = calculatedFootprint,
                        notes = noteInput
                    )
                    isSuccessLogged.value = true
                    quantityInput = ""
                    noteInput = ""
                    focusManager.clearFocus()
                }
            },
            enabled = (quantityInput.toDoubleOrNull() ?: 0.0) > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_save_activity"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save & Log Activity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Success notification banner
        AnimatedVisibility(visible = isSuccessLogged.value) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Successfully logged carbon activity!", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { isSuccessLogged.value = false }) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}


// --- TAB 2: AI REVIEWS & ASSIGNMENTS ---
@Composable
fun AiAdvisorScreen(viewModel: CarbonViewModel) {
    val aiPlan by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    var chatInput by remember { mutableStateOf("") }
    var subTab by remember { mutableStateOf(0) } // 0: AI Plan, 1: Conversation

    val context = LocalContext.current
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                chatInput = spokenText
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }) {
                Text("AI Recommendations", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = subTab == 1, onClick = { subTab = 1 }) {
                Text("Interactive Chat", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
        }

        AnimatedContent(targetState = subTab, label = "AI_SubTab_Transition") { targetSubTab ->
            if (targetSubTab == 0) {
                // PLAN RECOMMENDATION TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Gemini AI Carbon Audit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Automated analysis based on your recent transport, food and utilities spending logs.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAiLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Analyzing logs with Gemini AI...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        aiError?.let { err ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(err, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                if (aiPlan.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Your Voice Audit Assistant",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val isCurrentlySpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
                                            IconButton(
                                                onClick = {
                                                    viewModel.speak(aiPlan)
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.VolumeUp,
                                                    contentDescription = "Read Aloud",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (isCurrentlySpeaking) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.stopSpeaking()
                                                    },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.errorContainer)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.VolumeOff,
                                                        contentDescription = "Stop Speech",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = if (aiPlan.isNotEmpty()) aiPlan else "Log some everyday activities first, then request Gemini feedback analysis!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.generateFeedbackPlan(silent = false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_regenerate_ai_plan"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Re-Analyze and Sync Plans", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // CONVERSATION CHAT TAB
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chatMessages) { msg ->
                                val isUser = msg.sender == "user"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isUser) "You" else "AI Advisor 🎙️",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                                                )
                                                if (!isUser) {
                                                    IconButton(
                                                        onClick = { viewModel.speak(msg.text) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.VolumeUp,
                                                            contentDescription = "Read Aloud",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (isChatLoading) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask about savings, solar offsets...") },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to CarbonTrace Voice Assistant")
                                        }
                                        try {
                                            speechRecognizerLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Tap to speak",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (chatInput.isNotEmpty()) {
                                    viewModel.sendChatMessage(chatInput)
                                    chatInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("btn_chat_send")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}


// --- TAB 3: SETTINGS ---
@Composable
fun SettingsScreen(viewModel: CarbonViewModel) {
    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    var userNameInput by remember { mutableStateOf(userName) }
    var dailyTargetInput by remember { mutableStateOf(dailyTarget.toString()) }

    var isSavedConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Preferences & Configurations",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("User Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = { userNameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_username"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = dailyTargetInput,
                    onValueChange = { dailyTargetInput = it },
                    label = { Text("Daily Budget Target (kg CO₂e)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_budget"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Display & Appearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Switch between light and dark modes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    val isDarkFlow by viewModel.isDarkMode.collectAsStateWithLifecycle()
                    Switch(
                        checked = isDarkFlow,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        thumbContent = {
                            Icon(
                                imageVector = if (isDarkFlow) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val budget = dailyTargetInput.toDoubleOrNull() ?: 15.0
                viewModel.updateUserName(userNameInput)
                viewModel.updateDailyTarget(budget)
                isSavedConfirm = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_save_settings"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Apply & Save Preferences", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(visible = isSavedConfirm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.OfflinePin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Configurations saved successfully!", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { isSavedConfirm = false }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secure Session Logout Option
        val userEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Account Session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Logged in securely as: ${userEmail ?: "Active Profile"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
                    modifier = Modifier.fillMaxWidth().testTag("btn_logout")
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Exit App", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Securely Log Out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset/Help Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Danger Zone", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Clear and purge all stored metrics data. This action is irreversible.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.clearAllData() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("btn_purge_data")
                ) {
                    Text("Purge Database Records", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(viewModel: CarbonViewModel) {
    var isSignUp by remember { mutableStateOf(false) } // false = Login, true = SignUp

    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Clear inputs on mode change
    LaunchedEffect(isSignUp) {
        email = ""
        name = ""
        password = ""
        confirmPassword = ""
        errorMessage = null
        successMessage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = "CarbonTrace App Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CarbonTrace",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isSignUp) "Create your Carbon Profile" else "Access secure eco statistics",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Determine high contrast styling for light/dark theme modes
                val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
                val inputTextColor = if (isLightTheme) Color.Black else Color.White
                val inputLabelColor = if (isLightTheme) Color(0xFF333333) else Color(0xFFA2BFA3)
                val inputContainerColor = if (isLightTheme) Color(0xFFF5F7F4) else Color(0xFF2B2E2A)

                val inputTextFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = inputTextColor,
                    unfocusedTextColor = inputTextColor,
                    focusedContainerColor = inputContainerColor,
                    unfocusedContainerColor = inputContainerColor,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isLightTheme) Color(0xFFC1C9BA) else Color(0xFF4C5544),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = inputLabelColor.copy(alpha = 0.8f),
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = inputLabelColor.copy(alpha = 0.7f)
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_name_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = inputTextFieldColors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = inputTextFieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = inputTextFieldColors
                )

                if (isSignUp) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_confirm_password_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = inputTextFieldColors
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Error Message Notice
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Success Message Notice
                AnimatedVisibility(visible = successMessage != null) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Button
                val focusManager = LocalFocusManager.current
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        errorMessage = null
                        successMessage = null

                        if (isSignUp) {
                            if (name.trim().isEmpty() || email.trim().isEmpty() || password.isEmpty()) {
                                errorMessage = "Please fill in all blanks"
                                return@Button
                            }
                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@Button
                            }
                            viewModel.signUp(email, name, password) { success, msg ->
                                if (success) {
                                    successMessage = "Welcome aboard!"
                                } else {
                                    errorMessage = msg
                                }
                            }
                        } else {
                            if (email.trim().isEmpty() || password.isEmpty()) {
                                errorMessage = "Email and password are required"
                                return@Button
                            }
                            viewModel.login(email, password) { success, msg ->
                                if (!success) {
                                    errorMessage = msg
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auth_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isSignUp) "Register Account" else "Sign In",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Switch Mode toggle Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSignUp) "Already have an account?" else "New to CarbonTrace?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSignUp) "Sign In" else "Register",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { isSignUp = !isSignUp }
                            .padding(4.dp)
                            .testTag("auth_toggle_mode")
                    )
                }
            }
        }
    }
}
