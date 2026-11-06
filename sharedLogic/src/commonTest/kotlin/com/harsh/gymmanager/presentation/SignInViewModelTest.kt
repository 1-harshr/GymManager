package com.harsh.gymmanager.presentation

import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.AuthState
import com.harsh.gymmanager.domain.repository.SignInException
import com.harsh.gymmanager.domain.repository.SignInProblem
import com.harsh.gymmanager.presentation.signin.RESEND_WAIT_SECONDS
import com.harsh.gymmanager.presentation.signin.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeAuth : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        val codesSent = mutableListOf<String>()
        val verified = mutableListOf<Pair<String, String>>()
        var failSend: SignInProblem? = null
        var failVerify: SignInProblem? = null

        override suspend fun sendPhoneCode(phone: String) {
            failSend?.let { throw SignInException(it) }
            codesSent += phone
        }

        override suspend fun verifyPhoneCode(phone: String, code: String) {
            failVerify?.let { throw SignInException(it) }
            verified += phone to code
        }

        override suspend fun signOut() = Unit
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aShortNumberIsNotSent() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("98765")
        vm.sendCode()
        runCurrent()
        assertEquals(UiMessage.EnterPhone, vm.state.value.phoneError)
        assertTrue(auth.codesSent.isEmpty())
    }

    @Test
    fun pastedNumbersLoseTheirCountryCodeOrLeadingZero() {
        val vm = SignInViewModel(FakeAuth())
        vm.onPhoneChange("+91 98765 43210")
        assertEquals("9876543210", vm.state.value.phone)
        vm.onPhoneChange("09876543210")
        assertEquals("9876543210", vm.state.value.phone)
        vm.onPhoneChange("98765-432109")
        assertEquals("9876543210", vm.state.value.phone)
    }

    @Test
    fun sendingTheCodeMovesOnAndStartsTheResendWait() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("9000000001")
        vm.sendCode()
        runCurrent()
        assertEquals(listOf("+919000000001"), auth.codesSent)
        assertTrue(vm.state.value.awaitingCode)
        assertEquals(RESEND_WAIT_SECONDS, vm.state.value.resendInSeconds)

        vm.resendCode()
        runCurrent()
        assertEquals(1, auth.codesSent.size, "can't resend during the wait")

        advanceTimeBy(RESEND_WAIT_SECONDS * 1000L + 1)
        assertEquals(0, vm.state.value.resendInSeconds)
        vm.resendCode()
        runCurrent()
        assertEquals(2, auth.codesSent.size)
    }

    @Test
    fun theSixthDigitVerifiesTheCode() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("9000000001")
        vm.sendCode()
        runCurrent()
        vm.onCodeChange("12345")
        runCurrent()
        assertTrue(auth.verified.isEmpty())
        vm.onCodeChange("123456")
        runCurrent()
        assertEquals(listOf("+919000000001" to "123456"), auth.verified)
    }

    @Test
    fun aWrongCodeIsClearedWithAReason() = runTest(dispatcher) {
        val auth = FakeAuth().apply { failVerify = SignInProblem.WRONG_CODE }
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("9000000001")
        vm.sendCode()
        runCurrent()
        vm.onCodeChange("000000")
        runCurrent()
        val state = vm.state.value
        assertEquals(UiMessage.SignInFailed(SignInProblem.WRONG_CODE), state.codeError)
        assertEquals("", state.code)
        assertFalse(state.isBusy)
    }

    @Test
    fun noConnectionIsShownOnTheNumber() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth().apply { failSend = SignInProblem.NO_CONNECTION })
        vm.onPhoneChange("9000000001")
        vm.sendCode()
        runCurrent()
        assertEquals(UiMessage.SignInFailed(SignInProblem.NO_CONNECTION), vm.state.value.phoneError)
        assertFalse(vm.state.value.awaitingCode)
    }

    @Test
    fun changingTheNumberGoesBackAndStopsTheWait() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth())
        vm.onPhoneChange("9000000001")
        vm.sendCode()
        runCurrent()
        vm.changeNumber()
        assertFalse(vm.state.value.awaitingCode)
        assertEquals(0, vm.state.value.resendInSeconds)
        assertNull(vm.state.value.codeError)
        assertEquals("9000000001", vm.state.value.phone)
    }
}
