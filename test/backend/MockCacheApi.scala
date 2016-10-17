package backend

import javax.inject.{Inject, Singleton}

import play.api.cache.CacheApi

import scala.concurrent.duration.Duration

//noinspection ScalaDeprecation
@Singleton
final class MockCacheApi @Inject() extends CacheApi {

  var data: Map[String, Any] = Map.empty

  def set(key: String, value: Any, expiration: Duration): Unit = {
    this.data += key -> value
  }

  def remove(key: String): Unit = this.data -= key

  def getOrElse[A](key: String, expiration: Duration)(orElse: => A)(implicit evidence$1: ClassManifest[A]): A
  = this.data.getOrElse(key, orElse).asInstanceOf[A]

  def get[T](key: String)(implicit evidence$2: ClassManifest[T]): Option[T] = this.data.get(key).asInstanceOf[Option[T]]

}
