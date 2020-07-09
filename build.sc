import mill._, scalalib._

object bitcloudscore extends ScalaModule {
  def scalaVersion = "2.12.8"

  def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect:2.1.3", //for performing IO effects
    ivy"org.apache.sshd:sshd-core:2.5.1"   //for running ssh server
  )
}
