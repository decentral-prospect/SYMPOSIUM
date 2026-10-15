package com.decentralprospect.symposium

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.Signature
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import javax.crypto.KeyAgreement
import net.schmizz.sshj.SSHClient

@RunWith(AndroidJUnit4::class)
class SshCredentialStoreAndroidTest {
    @Test
    fun retrievesEcdsaServerHostKeyWhenTestEndpointIsProvided() {
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("sshTestHost")
        val port = arguments.getString("sshTestPort")?.toIntOrNull()
        assumeTrue("SSH integration test requires sshTestHost and sshTestPort", !host.isNullOrBlank() && port != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val pin = runBlocking {
            RemoteInstaller(context).observeSshHostKeyPin(requireNotNull(host), requireNotNull(port))
        }

        assertTrue(pin.startsWith("sha256/"))
    }

    @Test
    fun deviceProvidesEcSshPrimitivesThroughCompatibilityLayer() {
        assertNotNull(KeyFactory.getInstance("EC"))
        assertNotNull(KeyAgreement.getInstance("ECDH"))
        assertNotNull(Signature.getInstance("SHA256withECDSA"))
        assertTrue(enableAndroidEcdsaCompatibility())
        assertNotNull(KeyFactory.getInstance("ECDSA"))
    }

    @Test
    fun constructsSshClientWithAndroidCompatibleAlgorithms() {
        val config = AndroidCompatibleSshConfig()
        assertTrue(config.keyExchangeFactories.isNotEmpty())
        assertTrue(config.keyAlgorithms.isNotEmpty())
        assertTrue(config.cipherFactories.isNotEmpty())
        assertTrue(config.macFactories.isNotEmpty())
        val algorithmNames = config.keyAlgorithms.map { it.name }
        val rsaSha256Index = algorithmNames.indexOf("rsa-sha2-256")
        val rsaSha512Index = algorithmNames.indexOf("rsa-sha2-512")
        if (rsaSha256Index >= 0 && rsaSha512Index >= 0) {
            assertTrue(rsaSha256Index < rsaSha512Index)
        }
        SSHClient(config).use { }
    }

    @Test
    fun generatesAndReadsSshKeyFromAndroidKeystore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SshCredentialStore(context)
        val server = "192.0.2.25"
        val login = "symposium-api-${Build.VERSION.SDK_INT}"

        try {
            store.delete(server, login)
            val generated = store.generate(server, login)
            val publicKey = generated.public as RSAPublicKey
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(generated.private)
                update("symposium-ssh-api-${Build.VERSION.SDK_INT}".toByteArray())
                sign()
            }

            assertTrue(publicKey.modulus.bitLength() in sshRsaKeySizeCandidates())
            assertTrue(signature.isNotEmpty())
            assertNotNull(store.getSession(server, login))
        } finally {
            store.delete(server, login)
        }
    }
}
