package sh.bitclouds

import java.nio.file.Paths
import java.util
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.{Command, CommandFactory}
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.{ProcessShellFactory, UnknownCommand,InteractiveProcessShellFactory}
import org.apache.sshd.server.auth.pubkey.{PublickeyAuthenticator,CachingPublicKeyAuthenticator}
import java.security.PublicKey
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.common.SshConstants
import org.apache.sshd.server.ServerAuthenticationManager
import org.apache.sshd.common.util.GenericUtils
import org.apache.sshd.common.io.IoWriteFuture
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import scala.util.Try
import cats.effect.Resource

//the commented out imports below were used in part of some prior example code
//import org.apache.sshd.common.NamedFactory
//import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
//import org.apache.sshd.server.auth.password.{AcceptAllPasswordAuthenticator, PasswordAuthenticator}
//import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator
//import org.apache.sshd.server.scp.ScpCommandFactory
//import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory


/**
  * A "Just-in-time" authenticator.
  * Here we implement a custom PublickeyAuthenticator. From the perspective of
  * the end user, nothing will seem strange. Logging into our server just happens
  * to show a banner whereas many servers do not show banners these days. Nevertheless
  * banners are part of the core SSH protocol, so there is no reason to believe that
  * this would break any client implementations.
  * 
  * If, for example, the client does not display the banner, then the login will
  * eventually simply timeout.
  */
object JITPubkeyAuthenticator extends PublickeyAuthenticator {
  /**
    * This is the only method which needs to be implemented in order
    * for PublickeyAuthenticator to do its job.
    *
    * @param username
    * @param key
    * @param session
    * @return
    */
  def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    val formattedKey = BigInt(key.getEncoded).toString(16).take(20)
    val msg = s"""Welcome to bitclouds.sh! This server is a tragedy of the (un)commons.
                 |Attempting to authenticate your key that starts with (hex)$formattedKey
                 |Please pay the following invoice to continue: <invoice/link goes here>
                 |Awaiting payment [#""".stripMargin('|')

    // send initial banner
    sendBannerMessage(msg,session)

    // emulate some waiting
    for(i <- 1 to 15){
      Thread.sleep(1000)
      sendBannerMessage("#",session)
    }

    // choose randomly whether the user has "paid"
    scala.util.Random.nextBoolean match {
      case true => 
            sendBannerMessage("#####] Payment accepted. Access Granted for 4 hours. Enjoy!\n", session)
            true
      case false => 
            sendBannerMessage("\nXXXX] Sorry, access is denied. Reason: payment not received before timeout.\n\n",session)
            false
    }
  }

  private def sendBannerMessage(msg: String, session: ServerSession):IoWriteFuture = {
    val lang = ServerAuthenticationManager.WELCOME_BANNER_LANGUAGE
    val b = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_BANNER,msg.length + GenericUtils.length(lang) + java.lang.Long.SIZE)
    b.putString(msg)
    b.putString(lang)
    session.writePacket(b)
  }
}


object ServerMain extends IOApp {
  
  def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(println("setting up default sshd server"))
    sshd <- IO(SshServer.setUpDefaultServer())
    port = args.headOption.flatMap(s => Try(s.toInt).toOption).getOrElse(22)
    _ <- IO(sshd.setPort(port))
    _ <- IO(println(s"listening port set to $port"))
          // just a bogus hardcoded keypair for the server for now
    hostkey <- IO(Paths.get("serverkeys.pub"))
    _ <- IO(sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostkey)))
    _ <- IO(println("host key set, now setting up custom public key authenticator"))
          // caching authenticator necessary to reduce duplicate attempts
    _ <- IO(sshd.setPublickeyAuthenticator(new CachingPublicKeyAuthenticator(JITPubkeyAuthenticator)))
    _ <- IO(println("done. Now detecting default shell to serve."))
    _ <- IO(sshd.setShellFactory(new InteractiveProcessShellFactory ))
    _ <- IO(println("installing command factory"))
    _ <- IO(sshd.setCommandFactory(commandFactory))
    _ <- IO(println("starting server")) *> IO(sshd.start())
    _ <- IO(println("server started...press any key to shutdown"))
    k <- IO(io.StdIn.readChar)
    _ <- IO(println("shutting down"))
  } yield ExitCode.Success

  //COMMAND FACTORY STUFF BELOW
  // these are just some fake commands that could be "run" in this shell
  // create a regex which matches things like "factorial 5" or "factorial 3"
  val factorial = """factorial (\d+)""".r

  val commandFactory: CommandFactory=(_: ChannelSession, command: String) =>
    command match {
      case "woof" => new SimpleCommand {
        def handle = s"Hello, It is ${new java.util.Date()} on this box."
      }
      case "miaow" => (() => s"You said $command"): SimpleCommand // using single abstract method trick
      case factorial(num) => (() => s"You wanted factorial of $num"): SimpleCommand
      // case "throw" => {() => throw new Exception("throwing")}: SimpleCommand
      case _ => (() => s"You said $command, I don't know this command"): SimpleCommand
    }
}
