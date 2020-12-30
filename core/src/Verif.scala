package verif

import scala.util.Random
import java.lang.reflect.Field

import chisel3._
import chisel3.experimental.DataMirror
import chisel3.experimental.BundleLiterals._
import maltese.mc.{IsBad, IsConstraint}
import maltese.passes.Inline
import maltese.smt.solvers.Z3SMTLib
import maltese.smt

import scala.collection.mutable.{ListBuffer, Map}


trait Transaction extends IgnoreSeqInBundle { this: Bundle =>
  override def equals(that: Any): Boolean = {
    var result = this.getClass() == that.getClass()
    if (result) {
      val fields = this.getClass.getDeclaredFields
      for (field <- fields) {
        field.setAccessible(true)
        if (field.get(this).isInstanceOf[List[UInt]]) {
          result &= field.get(this).asInstanceOf[List[UInt]].map((x: UInt) => x.litValue()).sameElements(
            field.get(that).asInstanceOf[List[UInt]].map((x: UInt) => x.litValue()))
        } else {
          result &= field.get(this).asInstanceOf[Data].litValue() == field.get(that).asInstanceOf[Data].litValue()
        }
      }
    }
    result
  }

  override def toString(): String = {
    var result = this.className + "("

    val fields = this.getClass.getDeclaredFields
    for (field <- fields) {
      field.setAccessible(true)
      if (field.get(this).isInstanceOf[List[UInt]]) {
        result += field.getName + ": ("
        for (u <- field.get(this).asInstanceOf[List[UInt]]) {
          result += u.litValue().toString() + ", "
        }
        result = result.slice(0, result.length - 2) + "), "
      } else {
        result += field.getName + ": "+ field.get(this).asInstanceOf[Data].litValue().toString() + ", "
      }
    }
    result = result.slice(0, result.length - 2) + ")"

    result
  }
}

trait VerifRandomGenerator {
  def setSeed(seed: Long): Unit
  def getNextBool: Bool
  def getNextUInt(width: Int): UInt
  def getNextSInt(width: Int): SInt
  // Can add more types
}

class ScalaVerifRandomGenerator extends VerifRandomGenerator {
  private val r = Random
  r.setSeed(1234567890.toLong)

  // Set seed for randomization
  def setSeed(seed : Long): Unit = {
    r.setSeed(seed)
  }

  def getNextBool: Bool = {
    r.nextBoolean().B
  }

  def getNextUInt(width: Int): UInt = {
    (r.nextInt().abs % Math.pow(2, width).toInt).U(width.W)
  }

  def getNextSInt(width: Int): SInt = {
    ((r.nextInt().abs % Math.pow(2, width).toInt) - Math.pow(2, width - 1).toInt).S(width.W)
  }

}

class DummyVerifRandomGenerator extends VerifRandomGenerator {
  var current_val = -1
  def setSeed(seed: Long): Unit = {

  }

  def getNextBool: Bool = {
    current_val += 1
    (current_val % 2).B
  }

  def getNextUInt(width: Int): UInt = {
    current_val += 1
    (current_val % Math.pow(2, width).toInt).U(width.W)
  }

  def getNextSInt(width: Int): SInt = {
    current_val += 1
    ((current_val.abs % Math.pow(2, width).toInt) - Math.pow(2, width - 1).toInt).S(width.W)
  }
}

sealed trait RandomizationError
case class Unsat() extends RandomizationError
case class Timeout() extends RandomizationError

package object Randomization {
  implicit class VerifBundle[T <: Bundle](bundle: T) extends Bundle {
    // Caching no longer seems to work within implicit class, seems like the variable is cleared each time
    val declaredFields: Map[Class[_], Array[Field]] = Map[Class[_],Array[Field]]()

    // Want to have constraints structure saved with each bundle, not currently working (similar problem to above)
    // Functions defined here for reference
    var constraints: Map[String, ListBuffer[Data => Bool]] = Map[String, ListBuffer[Data => Bool]]()

    // Ignore
    def addConstraint(fieldName: String, constraint: Data => Bool): Unit = {
      constraints(fieldName) += constraint
    }

    def rand(constraint: T => Bool): Either[RandomizationError, T] = {
      class RandomBundleWrapper extends RawModule {
        val clock = IO(Input(Clock()))
        val b = IO(Input(bundle.cloneType))
        val c = constraint(b)
        dontTouch(c)
        dontTouch(b)
        withClock(clock) {
          chisel3.experimental.verification.assume(c)
        }
      }

      val (state, module) = ChiselCompiler.elaborate(() => new RandomBundleWrapper)
      val portNames = DataMirror.fullModulePorts(module).drop(2) // drop clock and top-level 'b' IO

      // turn firrtl into a transition system
      val (sys, _) = FirrtlToFormal(state.circuit, state.annotations)
      // inline all signals
      val inlinedSys = Inline.run(sys)

      val support = inlinedSys.inputs
      val constraints = inlinedSys.signals.filter(_.lbl == IsConstraint).map(_.e.asInstanceOf[smt.BVExpr])
      SMTSampler(support, constraints) match {
        case Some(sampler) =>
          val samples = sampler.run()
          // TODO: use more than one sample
          val model = samples.head.toMap

          val modelBinding = portNames.map(_._1).zipWithIndex.map { case (name, index) =>
            new Function1[T, (Data, Data)] {
              def apply(t: T): (Data, Data) = t.getElements(index) -> model(name).U
            }
          }
          val randomBundle = module.b.cloneType.Lit(modelBinding.toSeq:_*)
          Right(randomBundle)
        case None => Left(Unsat())
      }
    }

    // Pass in constraint map. A listbuffer of cosntraints is mapped to field names (text). Currently, only supports
    // independent constraints (no dependencies). Also, only works with non-clashing field names. Currently proof-of-
    // concept.
    def rand (constraint: Map[String, ListBuffer[Data => Bool]] = Map("" -> new ListBuffer[Data => Bool])) (implicit randgen: VerifRandomGenerator): T = {
      rand_helper(bundle, constraint)
      bundle.cloneType
    }

    // Helper function for rand
    def rand_helper(b : Bundle, constraints: Map[String, ListBuffer[Data => Bool]] = Map("" -> new ListBuffer[Data => Bool])) (implicit randgen: VerifRandomGenerator): Unit = {
      // Caching
      if (!declaredFields.contains(b.getClass)) {
        declaredFields += (b.getClass -> b.getClass.getDeclaredFields)
      }

      // Randomize individual fields. Currently, only the UInt and SInt fields use the constraint map.
      for (field <- declaredFields(b.getClass)) {
        field.setAccessible(true)

        field.get(b).asInstanceOf[Any] match {
          case _: Bool =>
            field.set(b, randgen.getNextBool)
          case bundle: Bundle =>
            rand_helper(bundle, constraints)
          case uval: UInt =>
            if (uval.getWidth != 0) {
              var newval = randgen.getNextUInt(uval.getWidth)
              if (constraints.contains(field.getName)) {
                while (!satisfy(newval, constraints(field.getName)).litToBoolean) {
                  newval = randgen.getNextUInt(uval.getWidth)
                }
              }
              field.set(b, newval)
            }
          case sval: SInt =>
            if (sval.getWidth != 0) {
              var newval = randgen.getNextSInt(sval.getWidth)
              if (constraints.contains(field.getName)) {
                while (!satisfy(newval, constraints(field.getName)).litToBoolean) {
                  newval = randgen.getNextSInt(sval.getWidth)
                }
              }
              field.set(b, newval)
            }
          case _: Data =>
            println(s"[VERIF] WARNING: Skipping randomization of unknown chisel type,value: " +
              s"(${field.getName}:${field.getType},${field.get(b)})")
          case _: Any =>
          // Do nothing
        }
      }
    }

    def satisfy(value: Data, constraints: ListBuffer[Data => Bool]): Bool = {
      var sat = true
      for (constraint <- constraints) {
        sat &= constraint(value).litToBoolean
      }
      sat.B
    }

    // Temporary function to print for debug
    // Print contents of transaction
    // Can only handle single-nested bundles for now
    def printContents: Unit = {
      print(this.getStringContents)
    }

    def getStringContents: String = {
      var result = ""
      for (field <- this.getClass.getDeclaredFields) {
        field.setAccessible(true)
        field.get(this).asInstanceOf[Any] match {
          case bundle: Bundle =>
            result += s"Bundle ${field.getName} {"
            for (field1 <- bundle.getClass.getDeclaredFields) {
              field1.setAccessible(true)
              // Hardcoded for single-nested bundles. Just for proof-of-concept
              if (field1.get(bundle).isInstanceOf[Bundle]) {
                val innerBundle = field1.get(bundle)
                result += s"Bundle ${field1.getName} {"
                for (field2 <- innerBundle.getClass.getDeclaredFields) {
                  field2.setAccessible(true)
                  result += s"(${field2.getName}, ${field2.get(innerBundle)}) "
                }
                result += "} "
              } else {
                result += s"(${field1.getName}, ${field1.get(bundle)}) "
              }
            }
            result += "} "
          case map: Map[Class[_],Array[Field]] =>
            // Listing out Map contents
            result += s"Map ${field.getName} {"
            for (key <- map.keys) {
              result += s"(key: "
              for (field1 <- map(key)) {
                field1.setAccessible(true)
                result += s"${field1.getName} "
              }
              result += ") "
            }
            result += "} "
          case _: VerifRandomGenerator =>
            result += s"RandomGen(${field.getName})"
          case _: Any =>
            result += s"(${field.getName}:${field.getType}, ${field.get(this)}) "
        }
      }
      result += "\n"
      result
    }
  }
}

package object outputChecker {
  def checkOutput[T](dutOutput : Array[_ <: T], dutFn : T => Any,
                     swOutput : Array[_ <: T], swFn : T => Any) : Boolean = {
    if (dutOutput.map(t => dutFn(t)).sameElements(swOutput.map(t => swFn(t)))) {
      println("***** PASSED *****")
      val outputsize = dutOutput.length
      println(s"All $outputsize transactions were matched.")
      true
    } else {
      println("***** FAILED *****")
      // Will need a better way of printing differences
      println("========DUT========")
      for (t <- dutOutput) {
        println(dutFn(t))
      }
      println("========GOLDEN MODEL========")
      for (t <- swOutput) {
        println(swFn(t))
      }
      false
    }
  }
}

