package verif

// import freechips.rocketchip.config.Parameters
// import freechips.rocketchip.unittest._
// import freechips.rocketchip.util.{DecoupledHelper}

import chisel3._
import chisel3.util._

case class CAMIOInTr(en: Bool, we: Bool, keyRe: UInt, keyWr: UInt, dataWr: UInt) extends Transaction {
//  override def cloneType = CAMIOInTr(en, we, keyRe, keyWr, dataWr).asInstanceOf[this.type]
}

case class CAMIOOutTr(found: Bool, dataRe: UInt) extends Transaction {
//  override def cloneType = CAMIOOutTr(found, dataRe).asInstanceOf[this.type]
}

class CAMIOIn (keyWidth: Int, dataWidth: Int) extends CAMIOInTr(Input(Bool()), Input(Bool()), Input(UInt(keyWidth.W)), Input(UInt(keyWidth.W)), Input(UInt(dataWidth.W))) {
  override def cloneType = new CAMIOIn(keyWidth, dataWidth).asInstanceOf[this.type]
}

class CAMIOOut (keyWidth: Int, dataWidth: Int) extends CAMIOOutTr(Output(Bool()), Output(UInt(dataWidth.W))) {
  override def cloneType = new CAMIOOut(keyWidth, dataWidth).asInstanceOf[this.type]
}

class ParameterizedCAMAssociative(keyWidth: Int, dataWidth: Int, memSizeWidth: Int) extends MultiIOModule {
  require(keyWidth >= 0)
  require(dataWidth >= 0)
  require(memSizeWidth >= 0)
//  val in = IO(CAMIOInTr(Input(Bool()), Input(Bool()), Input(UInt(keyWidth.W)), Input(UInt(keyWidth.W)), Input(UInt(dataWidth.W))))
//  val out = IO(CAMIOOutTr(Output(Bool()), Output(UInt(dataWidth.W))))
  val in = IO(new CAMIOIn(keyWidth,dataWidth))
  val out = IO(new CAMIOOut(keyWidth,dataWidth))

  // Defining our "Memory"
  val memorySize = math.pow(2, memSizeWidth).toInt
  val keyReg = RegInit(VecInit(Seq.fill(memorySize)(0.U(keyWidth.W))))
  val valReg = RegInit(VecInit(Seq.fill(memorySize)(0.U(dataWidth.W))))

  // Combinational Logic
  val wrIndex  = RegInit(0.U(memSizeWidth.W))
  val index = Wire(UInt(memSizeWidth.W))
  val found = (keyReg(index) === in.keyRe) && in.en

  when (in.we) {
    keyReg(wrIndex) := in.keyWr
    valReg(wrIndex) := in.dataWr
    wrIndex := wrIndex + 1.U
  } 

  when (in.en) {
    index := keyReg.indexWhere(_ === in.keyRe)
  } .otherwise {
    index := (memorySize - 1).U
  }

  // Outputs
  out.found := found
  out.dataRe := valReg(index)
}

// class CAMIODecoupled (keyWidth: Int, dataWidth: Int)(implicit p: Parameters) extends Bundle {
//   val en = Flipped(Decoupled(Bool()))
//   val we = Flipped(Decoupled(Bool()))
//   val found = Decoupled(Bool())
//   val keyWr = Flipped(Decoupled(UInt(keyWidth.W)))
//   val keyRe = Flipped(Decoupled(UInt(keyWidth.W)))
//   val dataWr = Flipped(Decoupled(UInt(dataWidth.W)))
//   val dataRe = Decoupled(UInt(dataWidth.W))
//   override def cloneType = new CAMIODecoupled(keyWidth, dataWidth)(p).asInstanceOf[this.type]
// }

// class ParameterizedCAMLIBDN(
//     keyWidth: Int,
//     dataWidth: Int,
//     memSizeWidth: Int
//   )(implicit p: Parameters) extends Module {
//   require(keyWidth >= 0)
//   require(dataWidth >= 0)
//   require(memSizeWidth >= 0)
//   val io = IO(new CAMIODecoupled(keyWidth,dataWidth))

//   // Output Queue
//   val outFound = Module(new Queue(Bool(), 8))
//   val outDataRe = Module(new Queue(UInt(dataWidth.W), 8))

//   // Defining our "Memory"
//   val memorySize = math.pow(2, memSizeWidth).toInt
//   val keyReg = RegInit(VecInit(Seq.fill(memorySize)(0.U(keyWidth.W))))
//   val valReg = RegInit(VecInit(Seq.fill(memorySize)(0.U(dataWidth.W))))

//   // Helper Input Registers
//   val tempEn = RegInit(false.B)
//   val tempWe = RegInit(false.B)
//   val tempKeyRe = RegInit(0.U(keyWidth.W))
//   val tempKeyWr = RegInit(0.U(keyWidth.W))
//   val tempDataWr = RegInit(0.U(dataWidth.W))

//   // Helper Output Registers
//   val tempFound = RegInit(false.B)
//   val tempDataRe = RegInit(0.U(dataWidth.W))

//   // Combinational Logic
//   val pushed = RegInit(true.B)
//   val written = RegInit(true.B)
//   val wrIndex  = RegInit(0.U(memSizeWidth.W))
//   val index  = RegInit(0.U(memSizeWidth.W))
//   val keyRe = RegInit(0.U(keyWidth.W))
//   val done = ((keyReg(index) === tempKeyRe) || (index === (memorySize-1).asUInt))
//   val found = (keyReg(index) === tempKeyRe) && tempEn
  

//   // DecoupledHelpers
//   val ioHelper = DecoupledHelper(
//     io.en.valid,
//     io.we.valid,
//     io.keyWr.valid,
//     io.keyRe.valid,
//     io.dataWr.valid,
//     pushed,
//     written)

//   val camfire = ioHelper.fire()

//   val outputHelper = DecoupledHelper(
//     outFound.io.enq.ready,
//     outDataRe.io.enq.ready)

//   val outfire = outputHelper.fire()

//   io.en.ready := ioHelper.fire(io.en.valid)
//   io.we.ready := ioHelper.fire(io.we.valid)
//   io.keyWr.ready := ioHelper.fire(io.keyWr.valid)
//   io.keyRe.ready := ioHelper.fire(io.keyRe.valid)
//   io.dataWr.ready := ioHelper.fire(io.dataWr.valid)

//   io.found <> outFound.io.deq
//   io.dataRe <> outDataRe.io.deq

//   outFound.io.enq.bits := found
//   outDataRe.io.enq.bits := valReg(index)

//   when (camfire) {
//     // printf(p"===========================\n")
//     pushed := false.B
//     written := false.B
//     outFound.io.enq.valid := false.B
//     outDataRe.io.enq.valid := false.B

//     // Reads
//     tempEn := io.en.bits
//     when (io.en.bits) {
//       index := 0.U
//       tempKeyRe := io.keyRe.bits
//       // printf(p"DUT keyRe = ${io.keyRe.bits}\n")
//     } .otherwise {
//       index := (memorySize - 1).U
//     }

//     // Writes
//     tempWe := io.we.bits
//     tempKeyWr := io.keyWr.bits
//     tempDataWr := io.dataWr.bits
//   } .elsewhen (!done) {
//     outFound.io.enq.valid := false.B
//     outDataRe.io.enq.valid := false.B

//     index := index + 1.U
//   } .elsewhen (done) {

//     // Add to output queue
//     when (outfire && !pushed) {
//       outFound.io.enq.valid := true.B
//       outDataRe.io.enq.valid := true.B
//       pushed := true.B
//     } .otherwise {
//       outFound.io.enq.valid := false.B
//       outDataRe.io.enq.valid := false.B
//     }

//     // Writing after reading
//     when (tempWe && pushed && !written) {
//       keyReg(wrIndex) := tempKeyWr
//       valReg(wrIndex) := tempDataWr
//       wrIndex := wrIndex + 1.U
//       written := true.B
//     } .elsewhen (pushed && !written) {
//       written := true.B
//     }
//   } .otherwise {
//     outFound.io.enq.valid := false.B
//     outDataRe.io.enq.valid := false.B
//   }
// }

// class CAMUnitTest(
//     keyWidth: Int = 8,
//     dataWidth: Int = 8,
//     memSizeWidth: Int = 8,
//     numTokens: Int = 4096,
//     timeout: Int = 500000
//   )(implicit p: Parameters) extends UnitTest(timeout) {

//   val dut = Module(new ParameterizedCAMLIBDN(keyWidth, dataWidth, memSizeWidth))
//   val reference = Module(new ParameterizedCAMAssociative(keyWidth, dataWidth, memSizeWidth))

//   val inputChannelMapping = Seq(
//     IChannelDesc("en", reference.io.en, dut.io.en),
//     IChannelDesc("we", reference.io.we, dut.io.we),
//     IChannelDesc("keyWr", reference.io.keyWr, dut.io.keyWr),
//     IChannelDesc("keyRe", reference.io.keyRe, dut.io.keyRe),
//     IChannelDesc("dataWr", reference.io.dataWr, dut.io.dataWr)
//   )
//   val outputChannelMapping = Seq(
//     OChannelDesc("found", reference.io.found, dut.io.found,
//       TokenComparisonFunctions.ignoreNTokens(1)),
//     OChannelDesc("dataRe", reference.io.dataRe, dut.io.dataRe,
//       TokenComparisonFunctions.ignoreNTokens(1))
//   )

//   io.finished := DirectedLIBDNTestHelper(inputChannelMapping, outputChannelMapping, numTokens)
// }
