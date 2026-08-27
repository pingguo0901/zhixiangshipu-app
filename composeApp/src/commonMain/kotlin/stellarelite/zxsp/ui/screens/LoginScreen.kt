package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canLogin = email.isNotBlank() && password.isNotBlank() && !loading

    fun doLogin() {
        if (!canLogin) return
        scope.launch {
            loading = true
            error = null
            val session = SupabaseClient.login(email.trim(), password)
            if (session.isSuccess) {
                val s = session.getOrNull()
                val staff = s?.user?.id?.let { SupabaseClient.fetchMyStaff(it) }
                if (staff != null && staff.is_active) {
                    SessionManager.setSession(
                        token = s.access_token,
                        staffId = staff.id,
                        staffName = staff.staff_name,
                        role = staff.role
                    )
                } else if (staff != null && !staff.is_active) {
                    error = "该账号已停用，请联系老板"
                } else {
                    error = "该账号未关联员工档案，请联系老板"
                }
            } else {
                error = session.exceptionOrNull()?.message ?: "登录失败"
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiningColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text("🍖", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "炙巷食铺",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = DiningColors.TextPrimary
        )
        Text(
            "内部员工管理端",
            fontSize = 14.sp,
            color = DiningColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "账号登录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiningColors.TextPrimary
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        error!!,
                        fontSize = 13.sp,
                        color = DiningColors.Error
                    )
                }

                Button(
                    onClick = { doLogin() },
                    enabled = canLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiningColors.Primary,
                        disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = DiningColors.Surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "登录即代表你已获得授权访问内部系统",
            fontSize = 11.sp,
            color = DiningColors.TextMuted
        )
    }
}
