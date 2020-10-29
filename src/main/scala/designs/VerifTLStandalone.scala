package designs

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink.TLRegisterNode
import verif.VerifTLBase

trait VerifTLStandaloneBlock extends LazyModule with VerifTLBase {
  // Commented out for now
//  //Diplomatic node for mem interface (OPTIONAL)
//  val mem: Option[MixedNode[TLMasterPortParameters, TLSlavePortParameters, TLEdgeIn, TLBundle,
//    TLMasterPortParameters, TLSlavePortParameters, TLEdgeOut, TLBundle]]
//
//  val ioMem = mem.map { m => {
//    val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
//    m :=
//      BundleBridgeToTL(TLMasterPortParameters(Seq(TLMasterParameters("bundleBridgeToTL")))) :=
//      ioMemNode
//    val ioMem = InModuleBody { ioMemNode.makeIO() }
//    ioMem
//  }}

  val ioInNode = BundleBridgeSource(() => TLBundle(verifTLUBundleParams))
  val ioOutNode = BundleBridgeSink[TLBundle]()

  val TLMaster: TLOutwardNode
  val TLSlave: TLInwardNode

   ioOutNode :=
     TLToBundleBridge(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(address = Seq(AddressSet(0x0, 0xfff)),
       supportsGet = TransferSizes(1, 8), supportsPutFull = TransferSizes(1,8))), beatBytes = 8)) :=
     TLMaster

  TLSlave :=
    BundleBridgeToTL(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1("bundleBridgeToTL")))) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}

class VerifTLRegBankSlave(implicit p: Parameters) extends LazyModule  {
  val device = new SimpleDevice("VerifTLRegBankSlave", Seq("veriftldriver,veriftlmonitor,testmaster")) // Not sure about compatibility list

  val TLSlave = TLRegisterNode(
    address = Seq(AddressSet(0x0, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  // Filler for now
  val TLMaster = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "testmaster",
    sourceId = IdRange(0,1),
    requestFifo = true,
    visibility = Seq(AddressSet(0x1000, 0xfff)))))))

  lazy val module = new LazyModuleImp(this) {
    val bigReg1 = RegInit(10.U(64.W))
    val bigReg2 = RegInit(11.U(64.W))
    val bigReg3 = RegInit(12.U(64.W))
    val bigReg4 = RegInit(13.U(64.W))

    // Will try to implement hardware FIFO (using Queue) for later examples
    TLSlave.regmap(
      0x00 -> Seq(RegField(64, bigReg1)),
      0x08 -> Seq(RegField(64, bigReg2)),
      0x10 -> Seq(RegField(64, bigReg3)),
      0x18 -> Seq(RegField(64, bigReg4))
    )
  }
}

// Example of manual Master
class VerifTLCustomMaster(implicit p: Parameters) extends LazyModule  {
  val device = new SimpleDevice("VerifTLCustomMaster", Seq("veriftldriver,veriftlmonitor,testmaster"))

  // Filler for now
  val TLSlave = TLRegisterNode(
    address = Seq(AddressSet(0x0, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

    val TLMaster = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
      name = "testmaster",
      sourceId = IdRange(0,1),
      requestFifo = true,
      visibility = Seq(AddressSet(0x0, 0xfff)))))))

  lazy val module = new LazyModuleImp(this) {
    val (out, edge) = TLMaster.out(0)
    val addr = RegInit(UInt(8.W), 0.U)
    val response = RegInit(UInt(5.W), 0.U)
    val alt = RegInit(Bool(), false.B)

    // Behavior: Read in address x and then write result in address + 0x20.U
    // Operates on addresses from 0x0 to 0x18

    // Offset by 8 needed here, will look into later
    when (alt) {
      out.a.bits := edge.Get(0.U, addr - 0x8.U, 3.U)._2
    } otherwise {
      out.a.bits := edge.Put(0.U, addr + 0x18.U, 3.U, response)._2
    }

    when (out.a.fire()) {
      when (!alt) {
        addr := addr + 0x8.U
      }
      alt := !alt
    }

    when (out.d.valid) {
      response := out.d.deq().data
    }

    // Hack to fix missing last instruction, fix later
    out.a.valid := addr < 0x20.U || (addr === 0x20.U && alt)
  }
}

class VerifTLMasterPattern(txns: Seq[Pattern])(implicit p: Parameters) extends LazyModule  {
  val device = new SimpleDevice("VerifTLMasterPattern", Seq("veriftldriver,veriftlmonitor,testmaster"))

  // Filler for now
  val TLSlave = TLRegisterNode(
    address = Seq(AddressSet(0x0, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  val patternp = LazyModule(new TLPatternPusher("patternpusher", txns))
  val TLMaster = patternp.node

  lazy val module = new LazyModuleImp(this) {
    val testIO = IO(new Bundle {
      val run = Input(Bool())
      val done = Output(Bool())
    })

    RegNext(patternp.module.io.run) := testIO.run
    testIO.done := patternp.module.io.done
  }
}

class VerifTLMasterFuzzer(implicit p: Parameters) extends LazyModule  {
  val device = new SimpleDevice("VerifTLMasterFuzzer", Seq("veriftldriver,veriftlmonitor,testmaster"))

  // Filler for now
  val TLSlave = TLRegisterNode(
    address = Seq(AddressSet(0x0, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  val TLMaster = TLFuzzer(30, inFlight=1)

  lazy val module = new LazyModuleImp(this) {}
}
