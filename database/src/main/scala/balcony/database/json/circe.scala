package balcony.database.json
import java.io.File
import java.net.URI
import java.nio.file.Path

import balcony.model.{Commit, EnvironmentBuild, Hash}
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.generic.semiauto._
object circe {

  implicit val hashCodec: Codec[Hash] = Codec.from[Hash](
    Decoder.decodeString.map(Hash.apply),
    Encoder.encodeString.contramap(_.value)
  )

  implicit val commitCodec: Codec[Commit] = Codec.from[Commit](
    Decoder.decodeString.map(str => Commit(Hash(str))),
    Encoder.encodeString.contramap(_.value.value)
  )

  implicit val uriCodec: Codec[URI] = Codec.from(
    Decoder.decodeString.map(URI.create),
    Encoder.encodeString.contramap(_.toString)
  )


  implicit val environmentBuildCodec: Codec[EnvironmentBuild] = deriveCodec

}
