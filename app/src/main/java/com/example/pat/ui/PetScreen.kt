package com.example.pat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.behavior.BehaviorSnapshot
import com.example.pat.behavior.BehaviorState
import com.example.pat.behavior.BehaviorController
import com.example.pat.ui.theme.PatTheme

/**
 * 测试用主界面。
 *
 * 当前阶段职责：展示传感器检测到的动作事件和当前行为状态。
 * 后续将替换为完整的角色视图、动画和交互界面。
 *
 * @param behaviorController 行为控制器，提供状态快照 StateFlow
 */
@Composable
fun PetScreen(
    behaviorController: BehaviorController,
    modifier: Modifier = Modifier
) {
    val snapshot by behaviorController.snapshot.collectAsState()

    PetScreenContent(
        snapshot = snapshot,
        modifier = modifier
    )
}

/**
 * 纯 UI 组件，便于预览和单元测试。
 */
@Composable
private fun PetScreenContent(
    snapshot: BehaviorSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "MotionPet",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 当前状态卡片
        Card(
            modifier = Modifier.fillMaxSize(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = when (snapshot.state) {
                    BehaviorState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                    BehaviorState.HAPPY -> MaterialTheme.colorScheme.tertiaryContainer
                    BehaviorState.HURT -> MaterialTheme.colorScheme.errorContainer
                    BehaviorState.ANGRY -> MaterialTheme.colorScheme.errorContainer
                    BehaviorState.SCARED -> MaterialTheme.colorScheme.errorContainer
                    BehaviorState.SLEEPING -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "当前状态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = snapshot.state.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "最近事件",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snapshot.lastEventName,
                    style = MaterialTheme.typography.titleLarge
                )
                if (snapshot.lastEventDetail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = snapshot.lastEventDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PetScreenPreview() {
    PatTheme {
        PetScreenContent(
            snapshot = BehaviorSnapshot(
                state = BehaviorState.HURT,
                lastEventName = "Impact",
                lastEventDetail = "强度: 0.75"
            )
        )
    }
}
