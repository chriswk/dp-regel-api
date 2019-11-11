package no.nav.dagpenger.regel.api.routing

import com.squareup.moshi.JsonDataException
import io.kotlintest.matchers.string.shouldNotEndWith
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.matchers.withClue
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifyAll
import no.nav.dagpenger.regel.api.db.BehovNotFoundException
import no.nav.dagpenger.regel.api.db.SubsumsjonId
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.models.*
import no.nav.dagpenger.regel.api.streams.DagpengerBehovProducer
import org.apache.kafka.clients.producer.RecordMetadata

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.Future

class V2BehovRouteTest {

    @Test
    fun `401 on unauthorized requests`() {
        withTestApplication(MockApi()) {
            // handleRequest(HttpMethod.Get, "/v2/behov/status/id").response.status() shouldBe HttpStatusCode.Unauthorized
            handleRequest(HttpMethod.Post, "/v2/behov").response.status() shouldBe HttpStatusCode.Unauthorized
            handleRequest(HttpMethod.Post, "/v2/behov") { addHeader("X-API-KEY", "notvalid") }
                    .response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `returns bad request when required fields are not present`() {
        shouldThrow<JsonDataException> {
            withTestApplication(MockApi()) {

                handleAuthenticatedRequest(HttpMethod.Post, "v2/behov") {
                    addHeader(HttpHeaders.ContentType, "application/json")
                    setBody("""
            {
                "aktorId": "1234",
                "eksternId": { "id": "1", "kontekst": "noe tull" },
                "beregningsdato": "2019-01-08"
            }
            """.trimIndent())
                }
            }
        }
    }

    @Test
    fun `Status when behov is done, pending or not found`() {
        val storeMock = mockk<SubsumsjonStore>(relaxed = false)
        every { storeMock.behovStatus("pending") } returns Status.Pending
        every { storeMock.behovStatus("done") } returns Status.Done("subsumsjonid")
        every { storeMock.behovStatus("notfound") } throws BehovNotFoundException("not found")

        withTestApplication(MockApi(
                subsumsjonStore = storeMock
        )) {

            handleAuthenticatedRequest(HttpMethod.Get, "v2/behov/status/pending")
                    .apply {

                        response.status() shouldBe HttpStatusCode.OK
                        withClue("Response should be handled") { requestHandled shouldBe true }
                        response.content shouldNotBe null
                        response.content shouldBe """{"status":"PENDING"}"""
                    }

            handleAuthenticatedRequest(HttpMethod.Get, "v2/behov/status/done")
                    .apply {
                        response.status() shouldBe HttpStatusCode.SeeOther
                        withClue("Response should be handled") { requestHandled shouldBe true }
                        response.headers[HttpHeaders.Location] shouldNotBe null
                        response.headers[HttpHeaders.Location] shouldBe "/subsumsjon/subsumsjonid"
                    }

            shouldThrow<BehovNotFoundException> {
                handleAuthenticatedRequest(HttpMethod.Get, "v2/behov/status/notfound")
            }
        }

        verifyAll {
            storeMock.behovStatus("pending")
            storeMock.behovStatus("done")
            storeMock.behovStatus("notfound")
        }
    }

    @Test
    fun `Valid json to behov endpoint should be accepted, saved and produce an event to Kafka`() {

        val obj: SubsumsjonStore = object : SubsumsjonStore {
            override fun insertSubsumsjon(subsumsjon: Subsumsjon, created: ZonedDateTime): Int {
                TODO("not implemented")
            }

            override fun delete(subsumsjon: Subsumsjon) {
                TODO("not implemented")
            }

            override fun getBehov(behovId: String): InternBehov {
                TODO("not implemented")
            }

            override fun insertBehov(behov: InternBehov): Int {
                return 1
            }

            override fun hentKoblingTilEkstern(eksternId: EksternId): BehandlingsId {
                return BehandlingsId.nyBehandlingsIdFraEksternId(eksternId)
            }

            override fun behovStatus(behovId: String): Status {
                TODO("not implemented")
            }

            override fun getSubsumsjon(behovId: String): Subsumsjon {
                TODO("not implemented")
            }

            override fun getSubsumsjonByResult(subsumsjonId: SubsumsjonId): Subsumsjon {
                TODO("not implemented")
            }
        }

        val produceSlot = slot<InternBehov>()
        val kafkaMock = mockk<DagpengerBehovProducer>(relaxed = true).apply {
            every { this@apply.produceEvent(behov = capture(produceSlot)) } returns mockk<Future<RecordMetadata>>()
        }

        withTestApplication(MockApi(
                obj,
                kafkaMock
        )) {

            handleAuthenticatedRequest(HttpMethod.Post, "v2/behov") {
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody("""
            {
                "aktorId": "1234",
                "eksternId": { "id": "1", "kontekst": "ESTIMASJON" },
                "beregningsdato": "2019-01-08",
                "manueltGrunnlag": 54200,
                "harAvtjentVerneplikt": true,
                "oppfyllerKravTilFangstOgFisk": true,
                "bruktInntektsPeriode":{"førsteMåned":"2011-07","sisteMåned":"2011-07"},
                "antallBarn": 1
            }
            """.trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.Accepted
                withClue("Response should be handled") { requestHandled shouldBe true }
                response.headers.contains(HttpHeaders.Location) shouldBe true
                response.headers[HttpHeaders.Location]?.let { location ->
                    location shouldStartWith "/behov/status/"
                    withClue("Behov id should be present") { location shouldNotEndWith "/behov/status/" }
                }
            }
        }

        with(produceSlot.captured) {
            behovId shouldNotBe null
            aktørId shouldBe "1234"
            behandlingsId shouldNotBe null
            beregningsDato shouldBe LocalDate.of(2019, 1, 8)
            harAvtjentVerneplikt shouldBe true
            oppfyllerKravTilFangstOgFisk shouldBe true
            bruktInntektsPeriode shouldBe InntektsPeriode(YearMonth.of(2011, 7), YearMonth.of(2011, 7))
            manueltGrunnlag shouldBe 54200
            antallBarn shouldBe 1
        }

        verifyAll {
            kafkaMock.produceEvent(any())
        }
    }
}
internal class BehovRequestMappingTestV2 {
    @Test
    fun `Add antallBarn to behov if not present in request`() {
        val behov = mapRequestToBehovV2(V2BehovRequest(
                aktorId = "aktorId",
                eksternId = EksternId("1", Kontekst.ESTIMASJON),
                beregningsdato = LocalDate.of(2019, 11, 7),
                harAvtjentVerneplikt = null,
                oppfyllerKravTilFangstOgFisk = null,
                bruktInntektsPeriode = null,
                manueltGrunnlag = null,
                antallBarn = null
        ))
        behov.antallBarn shouldBe 0
    }

    @Test
    fun `InntektsId should default to null if not present in request`() {
        val behov = mapRequestToBehovV2(V2BehovRequest(
                aktorId = "aktorId",
                eksternId = EksternId("1", Kontekst.ESTIMASJON),
                beregningsdato = LocalDate.of(2019, 11, 7),
                harAvtjentVerneplikt = null,
                oppfyllerKravTilFangstOgFisk = null,
                bruktInntektsPeriode = null,
                manueltGrunnlag = null,
                antallBarn = null
        ))
        behov.inntektsId shouldBe null
    }
}