package net.corda.core.serialization

import net.corda.core.contracts.*
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.tree
import net.corda.core.seconds
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.testing.MINI_CORP
import net.corda.testing.generateStateRef
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val TEST_PROGRAM_ID = TransactionSerializationTests.TestCash()

class TransactionSerializationTests {
    class TestCash : Contract {
        override val legalContractReference = SecureHash.sha256("TestCash")

        override fun verify(tx: TransactionForContract) {
        }

        data class State(
                val deposit: PartyAndReference,
                val amount: Amount<Currency>,
                override val owner: PublicKeyTree) : OwnableState {
            override val contract: Contract = TEST_PROGRAM_ID
            override val participants: List<PublicKeyTree>
                get() = listOf(owner)

            override fun withNewOwner(newOwner: PublicKeyTree) = Pair(Commands.Move(), copy(owner = newOwner))
        }
        interface Commands : CommandData {
            class Move() : TypeOnlyCommandData(), Commands
        }
    }

    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val fakeStateRef = generateStateRef()
    val inputState = StateAndRef(TransactionState(TestCash.State(depositRef, 100.POUNDS, DUMMY_PUBKEY_1), DUMMY_NOTARY), fakeStateRef)
    val outputState = TransactionState(TestCash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1), DUMMY_NOTARY)
    val changeState = TransactionState(TestCash.State(depositRef, 400.POUNDS, DUMMY_KEY_1.public.tree), DUMMY_NOTARY)


    lateinit var tx: TransactionBuilder

    @Before
    fun setup() {
        tx = TransactionType.General.Builder(DUMMY_NOTARY).withItems(
                inputState, outputState, changeState, Command(TestCash.Commands.Move(), arrayListOf(DUMMY_KEY_1.public.tree))
        )
    }

    @Test
    fun signWireTX() {
        tx.signWith(DUMMY_NOTARY_KEY)
        tx.signWith(DUMMY_KEY_1)
        val signedTX = tx.toSignedTransaction()

        // Now check that the signature we just made verifies.
        signedTX.verifySignatures()

        // Corrupt the data and ensure the signature catches the problem.
        signedTX.id.bits[5] = 0
        assertFailsWith(SignatureException::class) {
            signedTX.verifySignatures()
        }
    }

    @Test
    fun wrongKeys() {
        // Can't convert if we don't have signatures for all commands
        assertFailsWith(IllegalStateException::class) {
            tx.toSignedTransaction()
        }

        tx.signWith(DUMMY_KEY_1)
        tx.signWith(DUMMY_NOTARY_KEY)
        val signedTX = tx.toSignedTransaction()

        // Cannot construct with an empty sigs list.
        assertFailsWith(IllegalArgumentException::class) {
            signedTX.copy(sigs = emptyList())
        }

        // If the signature was replaced in transit, we don't like it.
        assertFailsWith(SignatureException::class) {
            val tx2 = TransactionType.General.Builder(DUMMY_NOTARY).withItems(inputState, outputState, changeState,
                    Command(TestCash.Commands.Move(), DUMMY_KEY_2.public.tree))
            tx2.signWith(DUMMY_NOTARY_KEY)
            tx2.signWith(DUMMY_KEY_2)

            signedTX.copy(sigs = tx2.toSignedTransaction().sigs).verifySignatures()
        }
    }

    @Test
    fun timestamp() {
        tx.setTime(TEST_TX_TIME, 30.seconds)
        tx.signWith(DUMMY_KEY_1)
        tx.signWith(DUMMY_NOTARY_KEY)
        val stx = tx.toSignedTransaction()
        assertEquals(TEST_TX_TIME, stx.tx.timestamp?.midpoint)
    }
}