package com.itv.scalapact.model

import com.itv.scalapact.{ScalaPactMock, ScalaPactMockConfig}
import com.itv.scalapact.shared.SslContextMap
import com.itv.scalapact.shared.typeclasses.{IPactReader, IPactStubber, IPactWriter, IScalaPactHttpClient, IScalaPactHttpClientBuilder}
import com.itv.scalapact.shared.Maps._

import scala.concurrent.duration._

class ScalaPactDescription(strict: Boolean,
                           consumer: String,
                           provider: String,
                           sslContextName: Option[String],
                           interactions: List[ScalaPactInteraction]) {

  /**
    * Adds interactions to the Pact. Interactions should be created using the helper object 'interaction'
    *
    * @param interaction [ScalaPactInteraction] definition
    * @return [ScalaPactDescription] to allow the builder to continue
    */
  def addInteraction(interaction: ScalaPactInteraction): ScalaPactDescription =
    new ScalaPactDescription(strict, consumer, provider, sslContextName, interactions :+ interaction)

  def addSslContextForServer(name: String): ScalaPactDescription =
    new ScalaPactDescription(strict, consumer, provider, Some(name), interactions)

  def runConsumerTest[F[_], A](test: ScalaPactMockConfig => A)(implicit options: ScalaPactOptions,
                                                               sslContextMap: SslContextMap,
                                                               pactReader: IPactReader,
                                                               pactWriter: IPactWriter,
                                                               httpClientBuilder: IScalaPactHttpClientBuilder[F],
                                                               pactStubber: IPactStubber): A = {
    implicit val client: IScalaPactHttpClient[F] =
      httpClientBuilder.build(2.seconds, sslContextName)
    ScalaPactMock.runConsumerIntegrationTest(strict)(
      finalise
    )(test)
  }

  private def finalise(implicit options: ScalaPactOptions): ScalaPactDescriptionFinal =
    ScalaPactDescriptionFinal(
      consumer,
      provider,
      sslContextName,
      interactions.map(i => i.finalise),
      options
    )
}

final case class ScalaPactDescriptionFinal(consumer: String,
                                     provider: String,
                                     serverSslContextName: Option[String],
                                     interactions: List[ScalaPactInteractionFinal],
                                     options: ScalaPactOptions) {
  def withHeaderForSsl: ScalaPactDescriptionFinal =
    copy(
      interactions = interactions.map(
        i =>
          i.copy(
            request = i.request
              .copy(headers = i.request.headers addOpt (SslContextMap.sslContextHeaderName -> i.sslContextName))
          )
      )
    )
}