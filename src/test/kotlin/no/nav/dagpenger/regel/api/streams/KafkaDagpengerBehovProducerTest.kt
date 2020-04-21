package no.nav.dagpenger.regel.api.streams

import io.kotest.matchers.numerics.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import no.nav.dagpenger.regel.api.models.BehandlingsId
import no.nav.dagpenger.regel.api.models.Behov
import no.nav.dagpenger.regel.api.models.InternBehov
import no.nav.dagpenger.regel.api.models.Kontekst
import no.nav.dagpenger.regel.api.models.RegelKontekst
import no.nav.dagpenger.regel.api.monitoring.HealthStatus
import no.nav.dagpenger.streams.Topics
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer

private object Kafka {
    val instance by lazy {
        // See https://docs.confluent.io/current/installation/versions-interoperability.html#cp-and-apache-kafka-compatibility
        KafkaContainer("5.3.1").apply { this.start() }
    }
}

internal class KafkaDagpengerBehovProducerTest {

    @Test
    fun `Produce packet should success`() {
        KafkaDagpengerBehovProducer(producerConfig("APP", Kafka.instance.bootstrapServers, null), Topics.DAGPENGER_BEHOV_PACKET_EVENT).apply {
            val metadata = produceEvent(InternBehov.fromBehov(
                behov = Behov(aktørId = "aktorId", vedtakId = 1, beregningsDato = LocalDate.now()),
                behandlingsId = BehandlingsId.nyBehandlingsIdFraEksternId(RegelKontekst("123", Kontekst.VEDTAK))
                )).get(5, TimeUnit.SECONDS)

            metadata shouldNotBe null
            metadata.hasOffset() shouldBe true
            metadata.serializedKeySize() shouldBeGreaterThan -1
            metadata.serializedValueSize() shouldBeGreaterThan -1
        }
    }

    @Test
    fun `Test kafka health`() {
        KafkaDagpengerBehovProducer(producerConfig("APP", Kafka.instance.bootstrapServers, null), Topics.DAGPENGER_BEHOV_PACKET_EVENT).apply {
            this.status() shouldBe HealthStatus.UP
        }
    }
}
