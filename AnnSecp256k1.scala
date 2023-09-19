//> using lib "org.typelevel::cats-effect:3.5.1"
//> using lib "org.scodec::scodec-bits::1.1.37"
//> using lib "vzxplnhqr::secp256k1-scala::0.1.1-SNAPSHOT"
//> using lib "com.github.mrdimosthenis::synapses:8.0.0"
//> using lib "org.typelevel::spire:0.18.0"

package ann

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.*
import synapses.lib.Net
import synapses.lib.Codec
import ecc.*
import Secp256k1.*
import scodec.bits.*
import scala.util.chaining.*
import scala.concurrent.duration.*
import spire.implicits.*
import java.io.*
object AnnSecp256k1 extends IOApp.Simple:

  //override protected def blockedThreadDetectionEnabled = true
  override def runtimeConfig = super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)

  /**
   * input layer is 1024 bits = 512 bits for point P and 512 bits for point G
   * hidden layer(s) just sort of chosen arbitrarily here
   * output layer is 256 bits = dlog(P) with base point G
   * */
  val randNet = IO(Net(List(1024,1024+512,1024+512,256), seed = 15432))

  def fitToPoint(net: Net, k: Z_n, learningRate: Double = 0.01): IO[Net] = for {
    pt <- IO(k * G)
    input <- IO(pt.bytes.bits ++ G.bytes.bits).map(_.toListDouble)
    expectedOutput = k.bytes.bits.toListDouble
    fittedNet <- IO(net.parFit(learningRate,input,expectedOutput))
  } yield fittedNet

  def printPredictionResults(net: Net)(using Random[IO]): IO[Unit] = for {
    k <- Z_n.rand
    pt <- IO(k * G)
    input <- IO(pt.bytes.bits ++ G.bytes.bits).map(_.toListDouble)
    expectedOutput = k.bytes.bits.toListDouble
    prediction <- IO(net.parPredict(input)).map(_.toBitVector)
    diffXor <- IO(prediction.xor(expectedOutput.toBitVector))
    score = diffXor.populationCount
    _ <- IO.println("expected:      \t" + k.bytes.bits.toBin)
    _ <- IO.println("predicted:     \t" + prediction.toBin)
    _ <- IO.println("diff ("+score+"):\t" + diffXor.toBin)
    logScore <- IO(BigDecimal(BigInt(signum =1, diffXor.bytes.toArray)).log(2))
    _ <- IO.println("logscore: " + logScore)
  } yield ()
  val run = (Random.scalaUtilRandom[IO].toResource).use {
    case (given Random[IO]) => for {
      net <- randNet
      ks <- LazyList.range(0,100000).parTraverse{_ => Z_n.rand }
      fittedNet <- ks.zipWithIndex.foldLeftM(net) {
        case (accumNet, (k, i)) => 
          fitToPoint(accumNet,k,learningRate = 0.01)
              .flatTap {
                testnet => if(i % 10 == 0)
                            IO.println(s"===== Test $i ")
                              *> (if(i % 1000 == 0) 
                                    writeBestToFile(testnet)
                                  else
                                    IO.unit)
                                *> printPredictionResults(testnet).start.void
                            else
                              IO.unit
              }
      }
    } yield ()
  }.cancelable(IO.println("canceled by user, shutting down ..."))

  val bestLogFileName = "logs/ann-secp256k1-bestyet.json"

  val bestLogFile = Resource.fromAutoCloseable(IO(new FileWriter(bestLogFileName)))

  def writeBestToFile(net: Net): IO[Unit] = bestLogFile.use{
    writer => IO.blocking(writer.write(net.json()))
  }
  
  extension(bits: BitVector)
    def toListDouble = bits.toIndexedSeq.map{ case true => 1.0; case false => 0.0 }.toList

  extension(listOfDoubles: List[Double])
    def toBitVector = listOfDoubles.map {
      case d if d >= 0.5 => true
      case _ => false
    }.pipe(bools => BitVector.bits(bools))