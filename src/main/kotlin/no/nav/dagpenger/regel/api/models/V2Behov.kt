package no.nav.dagpenger.regel.api.models

import de.huxhorn.sulky.ulid.ULID
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.regel.api.moshiInstance
import java.time.LocalDate
private val ulidGenerator = ULID()

data class V2Behov(
    val aktørId: String,
    val eksternId: EksternId,
    val beregningsDato: LocalDate,
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: InntektsPeriode? = null,
    val antallBarn: Int? = null,
    val manueltGrunnlag: Int? = null,
    val inntektsId: String? = null
) {
    fun toJson() = toJson(this)

    companion object Mapper {
        private val adapter = moshiInstance.adapter(V2Behov::class.java)

        fun toJson(internBehov: V2Behov): String = adapter.toJson(internBehov)

        fun fromJson(json: String): V2Behov? = adapter.fromJson(json)
    }
}

data class V2InternBehov(
    val behovId: String = ulidGenerator.nextULID(),
    val aktørId: String,
    val behandlingsId: BehandlingsId,
    val beregningsDato: LocalDate,
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: InntektsPeriode? = null,
    val antallBarn: Int? = null,
    val manueltGrunnlag: Int? = null,
    val inntektsId: String? = null

) {
    fun toJson() = toJson(this)
    fun toPacket() = toPacket(this)
    companion object Mapper {
        private val adapter = moshiInstance.adapter(V2InternBehov::class.java)

        fun toJson(internBehov: V2InternBehov): String = adapter.toJson(internBehov)

        fun fromJson(json: String): V2InternBehov? = adapter.fromJson(json)

        fun toPacket(internBehov: V2InternBehov): Packet = Packet("{}").apply {
            this.putValue(PacketKeys.BEHOV_ID, internBehov.behovId)
            this.putValue(PacketKeys.AKTØR_ID, internBehov.aktørId)
            when (internBehov.behandlingsId.eksternId.kontekst) {
                Kontekst.VEDTAK -> this.putValue(PacketKeys.VEDTAK_ID, internBehov.behandlingsId.eksternId.id)
            }
            this.putValue(PacketKeys.BEHANDLINGSID, internBehov.behandlingsId.id)
            this.putValue(PacketKeys.BEREGNINGS_DATO, internBehov.beregningsDato)
            internBehov.harAvtjentVerneplikt?.let { this.putValue(PacketKeys.HAR_AVTJENT_VERNE_PLIKT, it) }
            internBehov.oppfyllerKravTilFangstOgFisk?.let { this.putValue(PacketKeys.OPPFYLLER_KRAV_TIL_FANGST_OG_FISK, it) }
            internBehov.bruktInntektsPeriode?.let { this.putValue(PacketKeys.BRUKT_INNTEKTSPERIODE, it.toJson()) }
            internBehov.antallBarn?.let { this.putValue(PacketKeys.ANTALL_BARN, it) }
            internBehov.manueltGrunnlag?.let { this.putValue(PacketKeys.MANUELT_GRUNNLAG, it) }
            internBehov.inntektsId?.let { this.putValue(PacketKeys.INNTEKTS_ID, it) }
        }

        fun fromBehov(behov: V2Behov, behandlingsId: BehandlingsId): InternBehov {
            return InternBehov(
                    behandlingsId = behandlingsId,
                    aktørId = behov.aktørId,
                    harAvtjentVerneplikt = behov.harAvtjentVerneplikt,
                    oppfyllerKravTilFangstOgFisk = behov.oppfyllerKravTilFangstOgFisk,
                    manueltGrunnlag = behov.manueltGrunnlag,
                    beregningsDato = behov.beregningsDato,
                    bruktInntektsPeriode = behov.bruktInntektsPeriode,
                    antallBarn = behov.antallBarn,
                    inntektsId = behov.inntektsId
            )
        }
    }
}