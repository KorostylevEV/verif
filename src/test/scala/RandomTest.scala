package verif

import org.scalatest._
import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.{TreadleBackendAnnotation, VerilatorBackendAnnotation, WriteVcdAnnotation}
import designs.{CAMIO, ParameterizedCAMAssociative}
import chisel3.experimental.BundleLiterals._

case class InnerBundle[T <: Data](data: T, numb1: UInt = 0.U, numb2: UInt = 0.U) extends Bundle

case class NestedBundleTx[T <: Data](data: T, inner1: InnerBundle[UInt], inner2: InnerBundle[UInt],
                                     numb1: UInt = 0.U, implicit val r: VerifRandomGenerator) extends Transaction

class RandomTest extends FlatSpec with ChiselScalatestTester {
  it should "random test basic" in {
    test(new ParameterizedCAMAssociative(8,8,8))
      .withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
        implicit val randGen: VerifRandomGenerator = new ScalaVerifRandomGenerator

        // Testing with non-nested basic transactions
        val CTx = CAMIO(8, 8)
        for (_ <- 0 to 9) {
          CTx.rand.printContents
        }

        val DTx = DecoupledTX(165.U,0.U,1.U)
        for (_ <- 0 to 9) {
          DTx.rand.printContents
        }
        assert(true)
      }
  }

  it should "random test nested" in {
    test(new ParameterizedCAMAssociative(8,8,8))
      .withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
        implicit val randGen: VerifRandomGenerator = new ScalaVerifRandomGenerator
        // Testing with single nested transactions
        val NTx = NestedBundleTx(100.U, InnerBundle(1.U,1.U,1.U), InnerBundle(2.U,2.U,2.U), 3.U, randGen)
        for (_ <- 0 to 9) {
          NTx.rand.printContents
        }
        assert(true)
      }
  }
}