/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.mqtt.streaming
package impl

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Promise
import scala.concurrent.duration._

class RequestStateSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  val testKit = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "local packet router" should {
    "acquire a packet id" in {
      val registrant = testKit.createTestProbe[String]()
      val reply = Promise[LocalPacketRouter.Registered]()
      val router = testKit.spawn(LocalPacketRouter[String])
      router ! LocalPacketRouter.Register(registrant.ref, reply)
      reply.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(1))
    }

    "acquire two packet ids" in {
      val registrant = testKit.createTestProbe[String]()
      val reply1 = Promise[LocalPacketRouter.Registered]
      val reply2 = Promise[LocalPacketRouter.Registered]
      val router = testKit.spawn(LocalPacketRouter[String])
      router ! LocalPacketRouter.Register(registrant.ref, reply1)
      router ! LocalPacketRouter.Register(registrant.ref, reply2)
      reply1.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(1))
      reply2.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(2))
    }

    "acquire and release consecutive packet ids" in {
      val registrant = testKit.createTestProbe[String]()
      val reply1 = Promise[LocalPacketRouter.Registered]
      val reply2 = Promise[LocalPacketRouter.Registered]
      val reply3 = Promise[LocalPacketRouter.Registered]
      val reply4 = Promise[LocalPacketRouter.Registered]
      val router = testKit.spawn(LocalPacketRouter[String])

      router ! LocalPacketRouter.Register(registrant.ref, reply1)
      router ! LocalPacketRouter.Register(registrant.ref, reply2)
      reply1.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(1))
      reply2.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(2))

      router ! LocalPacketRouter.Unregister(PacketId(1))
      router ! LocalPacketRouter.Register(registrant.ref, reply3)
      reply3.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(3))

      router ! LocalPacketRouter.Unregister(PacketId(2))
      router ! LocalPacketRouter.Unregister(PacketId(3))
      router ! LocalPacketRouter.Register(registrant.ref, reply4)
      reply4.future.futureValue shouldBe LocalPacketRouter.Registered(PacketId(1))
    }

    "route a packet" in {
      val registrant = testKit.createTestProbe[String]()
      val reply = Promise[LocalPacketRouter.Registered]()
      val router = testKit.spawn(LocalPacketRouter[String])
      router ! LocalPacketRouter.Register(registrant.ref, reply)
      val registered = reply.future.futureValue
      val failureReply = Promise[String]
      router ! LocalPacketRouter.Route(registered.packetId, "some-packet", failureReply)
      registrant.expectMessage("some-packet")
      failureReply.future.isCompleted shouldBe false
    }

    "fail to route a packet" in {
      val reply = Promise[LocalPacketRouter.Registered]()
      val router = testKit.spawn(LocalPacketRouter[String])
      router ! LocalPacketRouter.Route(PacketId(1), "some-packet", reply)
      reply.future.failed.futureValue shouldBe LocalPacketRouter.CannotRoute
    }
  }

  "remote packet router" should {

    "route a packet" in {
      val packetId = PacketId(1)

      val registrant = testKit.createTestProbe[String]()
      val registerReply = Promise[RemotePacketRouter.Registered.type]()
      val failureReply1 = Promise[String]
      val failureReply2 = Promise[String]
      val router = testKit.spawn(RemotePacketRouter[String])

      router ! RemotePacketRouter.Register(registrant.ref, packetId, registerReply)
      registerReply.future.futureValue shouldBe RemotePacketRouter.Registered

      router ! RemotePacketRouter.Route(packetId, "some-packet", failureReply1)
      registrant.expectMessage("some-packet")
      failureReply1.future.isCompleted shouldBe false

      router ! RemotePacketRouter.Unregister(packetId)
      router ! RemotePacketRouter.Route(packetId, "some-packet", failureReply2)
      failureReply2.future.failed.futureValue shouldBe RemotePacketRouter.CannotRoute
      registrant.expectNoMessage(1.second)

    }
  }
}
