package verif

import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule}
import freechips.rocketchip.subsystem.WithoutTLMonitors
import verif.TLUtils._
import TLTransaction._

import scala.collection.immutable

class TLL2CacheTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = new WithoutTLMonitors

  it should "Elaborate L2" in {
    val TLL2 = LazyModule(new VerifTLL2Cache)
    test(TLL2.module).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      implicit val params = TLL2.in.params

      val L1Placeholder = new TLDriverMaster(c.clock, TLL2.in)
      val monitor = new TLMonitor(c.clock, TLL2.in)
      val monitor1 = new TLMonitor(c.clock, TLL2.out)

      val initialState = SlaveMemoryState(Seq(), immutable.HashMap[Int,Int](0 -> 0x1234, 8 -> 0x3333))
      val DRAMPlaceholder = new TLDriverSlave(c.clock, TLL2.out, initialState, testResponseWrapper)

      L1Placeholder.push(Seq(AcquireBlock(param = 1, addr = 0x8, size = 5)))

      c.clock.step(200)

      println("INNER (CORE)")
      for (t <- monitor.getMonitoredTransactions()) {
        println(t)
      }
      println("OUTER (DRAM)")
      for (t <- monitor1.getMonitoredTransactions()) {
        println(t)
      }
    }
  }

  // Ignoring test as new driver is no longer TLC compliance
  it should "Driver TLC Compliance Test" in {
    val TLL2 = LazyModule(new VerifTLL2Cache)
    test(TLL2.module).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      implicit val params = TLL2.in.params

      val L1Placeholder = new TLDriverMaster(c.clock, TLL2.in)
      val FuzzMonitor = new TLMonitor(c.clock, TLL2.in)
      val L1Monitor = new TLMonitor(c.clock, TLL2.in)
      val DRAMMonitor = new TLMonitor(c.clock, TLL2.out)

      val DRAMPlaceholder = new TLDriverSlave(c.clock, TLL2.out, SlaveMemoryState.init(), testResponseWrapper)

      val txns = Seq(
        // Two Acquires in a row, must be sequential
        AcquireBlock(param = 1, addr = 0x0, size = 3),
        AcquireBlock(param = 0, addr = 0x20, size = 3),
        // Cannot acquire until release completes
        ReleaseData(param = 0, addr = 0x20, data = 0x0, size = 3, source = 0),
        AcquireBlock(param = 1, addr = 0x40, size = 3),
        // L2 with sets = 2 will evict a block after third Acquire
      )

      val gen = new TLTransactionGenerator(standaloneSlaveParamsC.managers(0), TLL2.in.params, overrideAddr = Some(AddressSet(0x00, 0x1ff)),
        get = false, putPartial = false, putFull = false,
        burst = true, arith = false, logic = false, hints = false, acquire = true, tlc = true, cacheBlockSize = 3)
      val fuzz = new TLCFuzzer(params, gen, 3, txns, true)

      for (_<- 0 until 20) {
        val txns = fuzz.fuzzTxn(FuzzMonitor.getMonitoredTransactions().map({_.data}))
        L1Placeholder.push(txns)
        c.clock.step(5)
      }

//      println("PERM STATE")
//      val perm = L1Placeholder.permState
//      for (x <- perm.keys) {
//        print(s"(${x}, ${perm(x)}), ")
//      }
//      println("")

      println("INNER (CORE)")
      for (t <- L1Monitor.getMonitoredTransactions()) {
        println(t)
      }
      println("OUTER (DRAM)")
      for (t <- DRAMMonitor.getMonitoredTransactions()) {
        println(t)
      }
    }
  }

  it should "L2 SWTLFuzzer" ignore {

    val TLL2 = LazyModule(new VerifTLL2Cache)
    test(TLL2.module).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      implicit val params = TLL2.in.params

      val L1Placeholder = new TLDriverMaster(c.clock, TLL2.in)
      val FuzzMonitor = new TLMonitor(c.clock, TLL2.in)
      val L1Monitor = new TLMonitor(c.clock, TLL2.in)
      val DRAMMonitor = new TLMonitor(c.clock, TLL2.out)

      val DRAMPlaceholder = new TLDriverSlave(c.clock, TLL2.out, SlaveMemoryState.init(), testResponseWrapper)

      val gen = new TLTransactionGenerator(standaloneSlaveParamsC.managers(0), TLL2.in.params, overrideAddr = Some(AddressSet(0x00, 0x1ff)),
        get = false, putPartial = false, putFull = false,
        burst = true, arith = false, logic = false, hints = false, acquire = true, tlc = true, cacheBlockSize = 3)
      val fuzz = new TLCFuzzer(params, gen, 3)

      for (i <- 0 until 1000) {
        println(s"loop $i")
        println("GOT")
        val blah = FuzzMonitor.getMonitoredTransactions().map({_.data})
        for (t <- blah) {
          println(t)
        }
        val txns = fuzz.fuzzTxn(blah)
        println("SENDING")
        for (t <- txns) {
          println(t)
        }
        L1Placeholder.push(txns)
        println("MONITOR")
        for (t <- DRAMMonitor.getMonitoredTransactions()) {
          println(t)
        }
        c.clock.step(5)
      }

//      println("PERM STATE")
//      val perm = L1Placeholder.permState
//      for (x <- perm.keys) {
//        print(s"(${x}, ${perm(x)}), ")
//      }
//      println("")

      println("INNER (CORE)")
      for (t <- L1Monitor.getMonitoredTransactions()) {
        println(t)
      }
      println("OUTER (DRAM)")
      for (t <- DRAMMonitor.getMonitoredTransactions()) {
        println(t)
      }
    }
  }
}