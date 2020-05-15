package balcony.database.git

import java.nio.ByteBuffer

import cats.effect.IO
import io.bullet.borer.Cbor
import io.bullet.borer.compat.circe._
import io.circe.{Decoder, Encoder}

object Codecs {

  def encode[A : Encoder](a: A): ByteBuffer =
    Cbor.encode(a).toByteBuffer

  def decode[A : Decoder](bytes: ByteBuffer): IO[A] = IO.delay {
    Cbor.decode(bytes).to[A].value
  }
}