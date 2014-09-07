import akka.actor._
import akka.routing.RoundRobinPool
import akka.io.{ IO, Tcp, Udp }
import akka.pattern.pipe
import akka.util.ByteString
import anorm._
import anorm.SqlParser._
import spray.can.Http
import spray.http.HttpMethods.GET
import spray.http.{ Uri, HttpRequest, HttpResponse, StatusCodes }
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import scala.concurrent.duration._
import java.net.InetSocketAddress
import java.sql.{ DriverManager, Connection }

import EventProcessor._

object Main extends App {
  akka.Main.main(Array(classOf[Supervisor].getName))
}

class Supervisor extends Actor {
  val RESTART_STRATEGY = OneForOneStrategy(10, 1.minute) {
    case NonFatal(_) => SupervisorStrategy.Restart
  }

  val HTTP_ADDR = new InetSocketAddress("localhost", 6982)
  val UDP_ADDR = new InetSocketAddress("localhost", 4263)
  val REPO_COUNT = 5
  val PROC_COUNT = 20

  override val supervisorStrategy = RESTART_STRATEGY

  val repo = context.actorOf(
    RoundRobinPool(REPO_COUNT, supervisorStrategy = RESTART_STRATEGY)
      .props(Props(classOf[EventRepo])))

  val processor = context.actorOf(
    RoundRobinPool(PROC_COUNT, supervisorStrategy = RESTART_STRATEGY)
      .props(Props(classOf[EventProcessor], repo)))

  val udp = context.actorOf(
    Props(classOf[UdpListener], UDP_ADDR, processor))

  val http = context.actorOf(
    Props(classOf[HttpListener], HTTP_ADDR))

  def receive = Actor.emptyBehavior
}

object EventProcessor {
  //'127.0.0.1 - hostname [1371731248] "GET /123/foo/bar HTTP/1.1" 200 2326'
  val EventRegex = ".+?\\[(\\d+)\\]\\s\"\\w+\\s/(\\d+)/.+".r
  case class RawEvent(line: ByteString)
  case class EventData(timestamp: Long, userid: Long)
}

class UdpListener(address: InetSocketAddress, processor: ActorRef) extends Actor {
  IO(Udp)(context.system) ! Udp.Bind(self, address)

  def receive = {
    case Udp.Bound(_) => context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, _) => processor ! RawEvent(data)
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }
}

class EventProcessor(repo: ActorRef) extends Actor {
  def receive = {
    case RawEvent(raw) =>
      raw.decodeString("ASCII") match {
        case EventRegex(timestamp, userid) =>
          repo ! EventData(
            timestamp = timestamp.toLong,
            userid = userid.toLong)
        case line =>
      }
  }
}

class EventRepo extends Actor {
  object Persist

  private var events = Set.empty[EventData]

  context.system.scheduler.schedule(
    0.seconds, 10.seconds, self, Persist)(context.dispatcher)

  def receive = {
    case Persist => store()
    case e: EventData => events += e
  }

  def store(): Unit = if (events.nonEmpty) Try {
    DB.withTransaction { implicit connection =>
      val sql = events
        .map(e => s"(${e.userid}, ${e.timestamp})")
        .mkString("INSERT INTO events(user_id, time_stamp) VALUES", ",", ";")
      SQL(sql).executeInsert()
    }
    events = Set.empty
  }
}

class HttpListener(addr: InetSocketAddress) extends Actor {
  IO(Http)(context.system) ! Http.Bind(self,
    interface = addr.getHostName, port = addr.getPort)

  def receive = {
    case _: Http.Connected =>
      sender ! Http.Register(self)

    case HttpRequest(GET, uri@Uri.Path("/users"), _, _, _) =>
      import context.dispatcher
      Future {
        val args = uri.query.toMap
        val (start, end) = (args("start").toLong, args("end").toLong)
        DB.withTransaction { implicit connection =>
          SQL(s"""
          with unique_users as (
            select user_id
             from events
            where time_stamp between $start and $end
            group by user_id)
          select count(null)
            from unique_users
          """).as(scalar[Long].single)
        }
      } map {
        count => HttpResponse(entity = count.toString)
      } recover {
        case NonFatal(e) => HttpResponse(status = StatusCodes.BadRequest, entity = e.toString)
      } pipeTo sender

    case _: HttpRequest =>
      sender ! HttpResponse(status = StatusCodes.NotFound)
  }
}

object DB {
  Class.forName("org.postgresql.Driver").newInstance

  def withTransaction[A](block: Connection => A): A = {
    val connection = DriverManager.getConnection(
      "jdbc:postgresql://localhost/gameanalytics", "ga", "1111")
    try {
      connection.setAutoCommit(false)
      val result = block(connection)
      connection.commit()
      result
    } catch {
      case NonFatal(e) =>
        connection.rollback()
        throw e
    } finally {
      connection.close()
    }
  }
}
