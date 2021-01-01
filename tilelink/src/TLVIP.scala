package verif

import chisel3._
import chiseltest._
import freechips.rocketchip.tilelink._

import scala.collection.mutable.{HashMap, ListBuffer, MutableList, Queue}
import verifTLUtils._

import scala.reflect.runtime.universe.typeOf

// Functions for TL Master VIP
// Currently supports TL-UL, (TL-UH)
trait VerifTLMasterFunctions {
  def clk: Clock
  def TLChannels: TLBundle


  def pokeA(a: TLBundleA): Unit = {
    val aC = TLChannels.a
    aC.bits.opcode.poke(a.opcode)
    aC.bits.param.poke(a.param)
    aC.bits.size.poke(a.size)
    aC.bits.source.poke(a.source)
    aC.bits.address.poke(a.address)
    aC.bits.mask.poke(a.mask)
    aC.bits.data.poke(a.data)
  }

  def peekB(): TLBundleB = {
    val bC = TLChannels.b
    val opcode = bC.bits.opcode.peek()
    val param = bC.bits.param.peek()
    val size = bC.bits.size.peek()
    val source = bC.bits.source.peek()
    val address = bC.bits.address.peek()
    val mask = bC.bits.mask.peek()
    val data = bC.bits.data.peek()

    TLUBundleBHelper(opcode, param, size, source, address, mask, data)
  }

  def pokeC(c: TLBundleC): Unit = {
    val cC = TLChannels.c
    cC.bits.opcode.poke(c.opcode)
    cC.bits.param.poke(c.param)
    cC.bits.size.poke(c.size)
    cC.bits.source.poke(c.source)
    cC.bits.address.poke(c.address)
    cC.bits.data.poke(c.data)
    cC.bits.corrupt.poke(c.corrupt)
  }

  def peekD(): TLBundleD = {
    val dC = TLChannels.d
    val opcode = dC.bits.opcode.peek()
    val param = dC.bits.param.peek()
    val size = dC.bits.size.peek()
    val source = dC.bits.source.peek()
    val sink = dC.bits.sink.peek()
    val data = dC.bits.data.peek()
    val corrupt = dC.bits.corrupt.peek()

    TLUBundleDHelper(opcode, param, size, source, sink, data, corrupt)
  }

  def pokeE(e: TLBundleE): Unit = {
    val eC = TLChannels.e
    eC.bits.sink.poke(e.sink)
  }

  def writeA(a: TLBundleA): Unit = {
    val aC = TLChannels.a

    aC.valid.poke(true.B)
    pokeA(a)

    while(!aC.ready.peek().litToBoolean) {
      clk.step(1)
    }

    // Valid for at least one cycle
    clk.step(1)

    aC.valid.poke(false.B)
  }

  def readB(): TLBundleB = {
    val bC = TLChannels.b

    bC.ready.poke(true.B)

    while(!bC.valid.peek().litToBoolean) {
      clk.step(1)
    }

    // Read
    val result = peekB()

    // Ready for at least one cycle
    clk.step(1)

    bC.ready.poke(false.B)

    result
  }

  def writeC(c: TLBundleC): Unit = {
    val cC = TLChannels.c

    cC.valid.poke(true.B)
    pokeC(c)

    while(!cC.ready.peek().litToBoolean) {
      clk.step(1)
    }

    // Valid for at least one cycle
    clk.step(1)

    cC.valid.poke(false.B)
  }

  def readD(): TLBundleD = {
    val dC = TLChannels.d

    dC.ready.poke(true.B)

    while(!dC.valid.peek().litToBoolean) {
      clk.step(1)
    }

    // Read
    val result = peekD()

    // Ready for at least one cycle
    clk.step(1)

    result
  }

  def writeE(e: TLBundleE): Unit = {
    val eC = TLChannels.e

    eC.valid.poke(true.B)
    pokeE(e)

    while(!eC.ready.peek().litToBoolean) {
      clk.step(1)
    }

    // Valid for at least one cycle
    clk.step(1)

    eC.valid.poke(false.B)
  }

  def nonBlockingReadBD(b : ListBuffer[TLChannel], d : ListBuffer[TLChannel], source : Int): Unit = {
    if (TLChannels.b.ready.peek().litToBoolean && TLChannels.b.valid.peek().litToBoolean && TLChannels.b.bits.source.litValue() == source) {
      b += peekB()
    }
    if (TLChannels.d.ready.peek().litToBoolean && TLChannels.d.valid.peek().litToBoolean && TLChannels.d.bits.source.litValue() == source) {
      d += peekD()
    }
  }

  def writeChannel(bnd: TLChannel): Unit = {
    bnd match {
      case _: TLBundleA =>
        writeA(bnd.asInstanceOf[TLBundleA])
      case _: TLBundleC =>
        writeC(bnd.asInstanceOf[TLBundleC])
      case _: TLBundleE =>
        writeE(bnd.asInstanceOf[TLBundleE])
      case default =>
        println("ERROR: Non-valid bundle type. Can only write TLBundleA, TLBundleC, and TLBundleE.")
    }
  }

  // TODO Figure out why poking C and E does not work
  def reset(): Unit = {
    pokeA(TLUBundleAHelper())
//    pokeC(TLUBundleCHelper())
//    pokeE(TLUBundleEHelper())
    TLChannels.a.valid.poke(false.B)
    TLChannels.b.ready.poke(false.B)
    TLChannels.c.valid.poke(false.B)
    TLChannels.d.ready.poke(false.B)
    TLChannels.e.valid.poke(false.B)
  }
}

// Functions for TL Slave VIP
// Currently supports TL-UL, (TL-UH)
trait VerifTLSlaveFunctions {
  def clk: Clock
  def TLChannels: TLBundle

  def peekA(): TLBundleA = {
    val aC = TLChannels.a
    val opcode = aC.bits.opcode.peek()
    val param = aC.bits.param.peek()
    val size = aC.bits.size.peek()
    val source = aC.bits.source.peek()
    val address = aC.bits.address.peek()
    val mask = aC.bits.mask.peek()
    val data = aC.bits.data.peek()

    TLUBundleAHelper(opcode, param, size, source, address, mask, data)
  }

  def pokeB(b: TLBundleB): Unit = {
    val bC = TLChannels.b
    bC.bits.opcode.poke(b.opcode)
    bC.bits.param.poke(b.param)
    bC.bits.size.poke(b.size)
    bC.bits.source.poke(b.source)
    bC.bits.address.poke(b.address)
    bC.bits.mask.poke(b.mask)
    bC.bits.data.poke(b.data)
  }

  def peekC(): TLBundleC = {
    val cC = TLChannels.c
    val opcode = cC.bits.opcode.peek()
    val param = cC.bits.param.peek()
    val size = cC.bits.size.peek()
    val source = cC.bits.source.peek()
    val address = cC.bits.address.peek()
    val data = cC.bits.data.peek()
    val corrupt = cC.bits.corrupt.peek()

    TLUBundleCHelper(opcode, param, size, source, address, data, corrupt)
  }

  def pokeD(d: TLBundleD): Unit = {
    val dC = TLChannels.d
    dC.bits.opcode.poke(d.opcode)
    dC.bits.param.poke(d.param)
    dC.bits.size.poke(d.size)
    dC.bits.source.poke(d.source)
    dC.bits.sink.poke(d.sink)
    dC.bits.data.poke(d.data)
    dC.bits.corrupt.poke(d.corrupt)
  }

  def peekE(): TLBundleE = {
    val eC = TLChannels.e
    val sink = eC.bits.sink.peek()

    TLUBundleEHelper(sink)
  }

  def readA(): TLBundleA = {
    val aC = TLChannels.a

    aC.ready.poke(true.B)

    while(!aC.valid.peek().litToBoolean) {
      clk.step(1)
    }

    // Read
    val result = peekA()

    // Ready for at least one cycle
    clk.step(1)

    aC.ready.poke(false.B)

    result
  }

  def writeB(b: TLBundleB): Unit = {
    val bC = TLChannels.b

    bC.valid.poke(true.B)
    pokeB(b)

    while(!bC.ready.peek().litToBoolean) {
      clk.step(1)
    }

    // Valid for at least one cycle
    clk.step(1)

    bC.valid.poke(false.B)
  }

  def readC(): TLBundleC = {
    val cC = TLChannels.c

    cC.ready.poke(true.B)

    while(!cC.valid.peek().litToBoolean) {
      clk.step(1)
    }

    // Read
    val result = peekC()

    // Ready for at least one cycle
    clk.step(1)

    cC.ready.poke(false.B)

    result
  }

  def writeD(d: TLBundleD): Unit = {
    val dC = TLChannels.d

    dC.valid.poke(true.B)
    pokeD(d)

    while(!dC.ready.peek().litToBoolean) {
      clk.step(1)
    }

    // Valid for at least one cycle
    clk.step(1)

    dC.valid.poke(false.B)
  }

  def readE(): TLBundleE = {
    val eC = TLChannels.e

    eC.ready.poke(true.B)

    while(!eC.valid.peek().litToBoolean) {
      clk.step(1)
    }

    // Read
    val result = peekE()

    // Ready for at least one cycle
    clk.step(1)

    eC.ready.poke(false.B)

    result
  }

  def writeChannel(bnd: TLChannel): Unit = {
    bnd match {
      case _: TLBundleB =>
        writeB(bnd.asInstanceOf[TLBundleB])
      case _: TLBundleD =>
        writeD(bnd.asInstanceOf[TLBundleD])
      case default =>
        println("ERROR: Non-valid bundle type. Can only write TLBundleB and TLBundleD.")
    }
  }

  def reset(): Unit = {
//    pokeB(TLUBundleBHelper())
    pokeD(TLUBundleDHelper())
    TLChannels.a.ready.poke(false.B)
//    TLChannels.b.valid.poke(false.B)
//    TLChannels.c.ready.poke(false.B)
    TLChannels.d.valid.poke(false.B)
//    TLChannels.e.ready.poke(false.B)
  }

  def process(req: TLBundleA): Unit
}
trait VerifTLMonitorFunctions {
  def clk: Clock
  def TLChannels: TLBundle

  def peekA(): TLBundleA = {
    val aC = TLChannels.a
    val opcode = aC.bits.opcode.peek()
    val param = aC.bits.param.peek()
    val size = aC.bits.size.peek()
    val source = aC.bits.source.peek()
    val address = aC.bits.address.peek()
    val mask = aC.bits.mask.peek()
    val data = aC.bits.data.peek()

    TLUBundleAHelper(opcode, param, size, source, address, mask, data)
  }

  def peekB(): TLBundleB = {
    val bC = TLChannels.b
    val opcode = bC.bits.opcode.peek()
    val param = bC.bits.param.peek()
    val size = bC.bits.size.peek()
    val source = bC.bits.source.peek()
    val address = bC.bits.address.peek()
    val mask = bC.bits.mask.peek()
    val data = bC.bits.data.peek()

    TLUBundleBHelper(opcode, param, size, source, address, mask, data)
  }

  def peekC(): TLBundleC = {
    val cC = TLChannels.c
    val opcode = cC.bits.opcode.peek()
    val param = cC.bits.param.peek()
    val size = cC.bits.size.peek()
    val source = cC.bits.source.peek()
    val address = cC.bits.address.peek()
    val data = cC.bits.data.peek()
    val corrupt = cC.bits.corrupt.peek()

    TLUBundleCHelper(opcode, param, size, source, address, data, corrupt)
  }

  def peekD(): TLBundleD = {
    val dC = TLChannels.d
    val opcode = dC.bits.opcode.peek()
    val param = dC.bits.param.peek()
    val size = dC.bits.size.peek()
    val source = dC.bits.source.peek()
    val sink = dC.bits.sink.peek()
    val data = dC.bits.data.peek()
    val corrupt = dC.bits.corrupt.peek()

    TLUBundleDHelper(opcode, param, size, source, sink, data, corrupt)
  }

  def peekE(): TLBundleE = {
    val eC = TLChannels.e
    val sink = eC.bits.sink.peek()

    TLUBundleEHelper(sink)
  }

  // Non-HW defined "fire" method
  def fire(channel: Chisel.DecoupledIO[TLChannel]) : Boolean = {
    channel.ready.peek().litToBoolean && channel.valid.peek().litToBoolean
  }

  // For TL-UL, TL-UH
  def readAD(): List[TLChannel] = {
    var results = ListBuffer[TLChannel]()

    if (fire(TLChannels.a)) {
      results += peekA()
    }
    if (fire(TLChannels.d)) {
      results += peekD()
    }

    results.toList
  }

  // For TL-C
  def readABCDE(): List[TLChannel] = {
    var results = ListBuffer[TLChannel]()

    if (fire(TLChannels.a)) {
      results += peekA()
    }
    if (fire(TLChannels.b)) {
      results += peekD()
    }
    if (fire(TLChannels.c)) {
      results += peekD()
    }
    if (fire(TLChannels.d)) {
      results += peekD()
    }
    if (fire(TLChannels.e)) {
      results += peekD()
    }

    results.toList
  }
}

// TLDriver acting as a Master node
class TLDriverMaster(clock: Clock, interface: TLBundle) extends VerifTLMasterFunctions {
  val clk = clock
  val TLChannels = interface

  val inputTransactions = Queue[TLChannel]()

  def push(tx: Seq[TLTransaction]): Unit = {
    for (t <- tx) {
      inputTransactions ++= TLTransactiontoTLBundles(t)
    }
  }

  fork {
    // Ready always high (TL monitor always receiving in transactions)
    interface.d.ready.poke(true.B)
    while (true) {
      if (!inputTransactions.isEmpty) {
        val t = inputTransactions.dequeue()
        writeChannel(t)
        clock.step()
      } else {
        clock.step()
      }
    }
  }
}

// TLDriver acting as a Master node (New Design) (For TL-C)
class TLDriverMasterNew(clock: Clock, interface: TLBundle) extends VerifTLMasterFunctions {
  val clk = clock
  val TLChannels = interface

  // Testbench given Transactions
  val inputTransactions = ListBuffer[TLTransaction]()

  // Internal Structures
  // Internal states (Maps address to permissions and address to data) -- Users should not interface with this
  // Permissions: 0 - None, 1 - Read (Branch), 2 - Read/Write (Tip), -1 - Waiting for Grant/Ack
  val permState = HashMap[Int,Int]()
  val dataState = HashMap[Int,Int]()
  // Used for state processing (check if permissions/data were given etc)
  val bOutput = ListBuffer[TLChannel]()
  val dOutput = ListBuffer[TLChannel]()
  // Used for acquire addressing (!!! supports only one acquire in-flight) TODO use source as IDs
  var acquireInFlight = false
  var acquireAddr = 0.U
  // Used for release addressing (!!! supports only one release in-flight) TODO use source as IDs
  var releaseInFlight = false
  // Received transactions to be processed
  val tlProcess = ListBuffer[TLTransaction]()
  // TLBundles to be pushed
  val queuedTLBundles = Queue[TLChannel]()
  // Permission map for Acquire and Release
  val acquirePermMap = Map[Int,Int](0 -> 1, 1 -> 2, 2 -> 2)
  val releasePermMap = Map[Int,Int](0 -> 1, 1 -> 0, 2 -> 0, 3 -> 2, 4 -> 1, 5 -> 0)

  // Generates input transactions and adds to inputTransactions Queue
  // Takes into account of inputTransactions and processes TL-C specifics (Permissions/data etc)
  def process(): Unit = {

    // Process output transactions
    if (bOutput.nonEmpty || dOutput.nonEmpty) {
      if (isCompleteTLTxn(bOutput.toList)) {
        tlProcess += TLBundlestoTLTransaction(bOutput.toList)
        bOutput.clear()
      } else if (isCompleteTLTxn(dOutput.toList)) {
        tlProcess += TLBundlestoTLTransaction(dOutput.toList)
        dOutput.clear()
      }
    }

    var processIndex = 0
    while (processIndex < tlProcess.length) {
      val tlTxn = tlProcess(processIndex)
      tlTxn match {
        case _ : Grant =>
          val txnc = tlTxn.asInstanceOf[Grant]
          if (!txnc.denied.litToBoolean) {
            // Writing permissions
            val intSize = 1 << txnc.size.litValue().toInt
            writeData(state = permState, size = txnc.size, address = acquireAddr, datas = List.fill(intSize)(txnc.param), masks = List.fill(intSize)(0x3.U))

            queuedTLBundles ++= TLTransactiontoTLBundles(GrantAck(sink = txnc.sink))
            tlProcess.remove(0)
          }
        case _ : GrantData =>
          val txnc = tlTxn.asInstanceOf[GrantData]
          if (!txnc.denied.litToBoolean) {
            // Writing permissions and data
            val intSize = 1 << txnc.size.litValue().toInt
            writeData(state = permState, size = txnc.size, address = acquireAddr, datas = List.fill(intSize)(txnc.param), masks = List.fill(intSize)(0x3.U))
            writeData(state = dataState, size = txnc.size, address = acquireAddr, datas = List(txnc.data), masks = List(0xff.U))

            queuedTLBundles ++= TLTransactiontoTLBundles(GrantAck(sink = txnc.sink))
            tlProcess.remove(0)
          }
        case _ : GrantDataBurst =>
          val txnc = tlTxn.asInstanceOf[GrantDataBurst]
          if (!txnc.denied.litToBoolean) {
            // Writing permissions and data
            val intSize = 1 << txnc.size.litValue().toInt
            writeData(state = permState, size = txnc.size, address = acquireAddr, datas = List.fill(intSize)(txnc.param), masks = List.fill(intSize)(0x3.U))
            writeData(state = dataState, size = txnc.size, address = acquireAddr, datas = txnc.datas, masks = List.fill(intSize)(0xff.U))

            queuedTLBundles ++= TLTransactiontoTLBundles(GrantAck(sink = txnc.sink))
            tlProcess.remove(0)
          }
        case _ : ProbePerm => // Probe (Return ProbeAck) Don't process if pending Release Ack
          if (!releaseInFlight) {
            val txnc = tlTxn.asInstanceOf[ProbePerm]
            // Writing permissions
            val intSize = 1 << txnc.size.litValue().toInt
            writeData(state = permState, size = txnc.size, address = txnc.addr, datas = List.fill(intSize)(txnc.param), masks = List.fill(intSize)(0x3.U))

            queuedTLBundles ++= TLTransactiontoTLBundles(ProbeAck(param = txnc.param, size = txnc.param, source = txnc.source, addr = txnc.addr))
            tlProcess.remove(0)
          } else {
            processIndex += 1
          }
        case _ : ProbeBlock => // Probe (Return ProbeAck or ProbeAckData based off perms) Don't process if pending Release Ack
          if (!releaseInFlight) {
            val txnc = tlTxn.asInstanceOf[ProbePerm]
            // Reading old permissions (only head since whole block shares same permissions)
            val oldPerm = permState(txnc.addr.litValue().toInt) // If address not found, then something is wrong/broken

            // Writing permissions
            val intSize = 1 << txnc.size.litValue().toInt
            writeData(state = permState, size = txnc.size, address = txnc.addr, datas = List.fill(intSize)(txnc.param), masks = List.fill(intSize)(0x3.U))

            // If old permission included write access, need to send back dirty data
            if (oldPerm == 2) {
              queuedTLBundles ++= TLTransactiontoTLBundles(ProbeAckDataBurst(param = txnc.param, size = txnc.param, source = txnc.source, addr = txnc.addr,
                datas = readData(dataState, size = txnc.size, address = txnc.addr, mask = 0xff.U)))
            } else {
              queuedTLBundles ++= TLTransactiontoTLBundles(ProbeAck(param = txnc.param, size = txnc.param, source = txnc.source, addr = txnc.addr))
            }
            tlProcess.remove(0)
          } else {
            processIndex += 1
          }
        case _ : ReleaseAck =>
          // Now able to queue up more releases
          releaseInFlight = false
        case _ : Get | _ : PutFull | _ : PutFullBurst | _ : PutPartial | _ : PutPartialBurst | _ : ArithData |
          _ : ArithDataBurst | _ : LogicData | _ : LogicDataBurst | _ : Intent =>

          // Using the testResponse slave function
          val results = testResponse(input = tlTxn, state = dataState, fwd = true.B)

          queuedTLBundles ++= TLTransactiontoTLBundles(results._1)
          tlProcess.remove(0)
        case _ =>
          // Response that requires no processing
          tlProcess.remove(0)
      }
    }

    // Determine input transactions
    // Currently limit inFlight instructions, as unsure on handling overloading L2 TODO update
    var inputIndex = 0
    while (queuedTLBundles.length < 8 && inputTransactions.nonEmpty) {
      val tlTxn = inputTransactions(inputIndex)

      tlTxn match {
        case _ : AcquirePerm | _ : AcquireBlock => // Don't issue if pending Grant or Release Ack
          if (acquireInFlight || releaseInFlight) {
            inputIndex += 1
          } else {
            acquireInFlight = true

            tlTxn match {
              case _ : AcquirePerm => acquireAddr = tlTxn.asInstanceOf[AcquirePerm].addr
              case _ : AcquireBlock => acquireAddr = tlTxn.asInstanceOf[AcquireBlock].addr
            }

            queuedTLBundles ++= TLTransactiontoTLBundles(tlTxn)
            inputTransactions.remove(inputIndex)
          }
        case _ : Release | _ : ReleaseData | _ : ReleaseDataBurst => // Don't issue if pending Grant or Release Ack
          if (acquireInFlight || releaseInFlight) {
            inputIndex += 1
          } else {
            releaseInFlight = true

            var releaseAddr = 0.U
            var releaseSize = 0.U
            var releaseParam = 0.U
            tlTxn match {
              case _ : Release =>
                val txnc = tlTxn.asInstanceOf[Release]
                releaseAddr = txnc.addr
                releaseSize = txnc.size
                releaseParam = txnc.param
              case _ : ReleaseData =>
                val txnc = tlTxn.asInstanceOf[ReleaseData]
                releaseAddr = txnc.addr
                releaseSize = txnc.size
                releaseParam = txnc.param
              case _ : ReleaseDataBurst =>
                val txnc = tlTxn.asInstanceOf[ReleaseDataBurst]
                releaseAddr = txnc.addr
                releaseSize = txnc.size
                releaseParam = txnc.param
            }
            // Only modifying perms since transaction has data already
            // TODO Hard to randomly generate releases... unless generator has access to state...
            val intSize = 1 << releaseSize.litValue().toInt
            writeData(state = permState, size = releaseSize, address = releaseAddr, datas = List.fill(intSize)(releaseParam), masks = List.fill(intSize)(0x3.U))

            queuedTLBundles ++= TLTransactiontoTLBundles(tlTxn)
            inputTransactions.remove(inputIndex)
          }
        case _ =>
          // TODO Maybe add check if we already have data that is being requested (eg It doesn't make sense to Get an address that we have cached already)
          // TODO But This may also check the forwarding functionality?
          queuedTLBundles ++= TLTransactiontoTLBundles(tlTxn)
          inputTransactions.remove(inputIndex)
      }
    }
  }

  // Users can hardcode specific transactions
  def push(tx: Seq[TLTransaction]): Unit = {
    for (t <- tx) {
      inputTransactions ++= tx
    }
  }

  fork {
    // Ready always high (Always receiving in transactions)
    interface.b.ready.poke(true.B)
    interface.d.ready.poke(true.B)
    while (true) {
      if (!queuedTLBundles.isEmpty) {
        val t = queuedTLBundles.dequeue()
        writeChannel(t)
      } else {
        // Generate more transactions if available
        process()
      }
      // Read on every cycle if available
      nonBlockingReadBD(bOutput, dOutput, source = 0)
      clock.step()
    }
  }
}

// TLDriver acting as a Slave node
// Takes in a response function for processing requests
class TLDriverSlave[S](clock: Clock, interface: TLBundle, initState : S, response: (TLTransaction, S) =>
  (TLTransaction, S)) extends VerifTLSlaveFunctions {

  val clk = clock
  val TLChannels = interface
  val txns = ListBuffer[TLChannel]()
  var state = initState

  def setState (newState : S) : Unit = {
    state = newState;
  }

  def getState () : S = {
    state
  }

  // Responsible for collecting requests and calling on the user-defined response method
  // Currently only supports single source
  def process(a : TLBundleA) : Unit = {
    // Adding request to buffer (to collect for burst)
    txns += a

    // Checking if list buffer has complete TLTransaction
    if (isCompleteTLTxn(txns.toList)) {
      // Converting to TLTransaction
      val tltxn = TLBundlestoTLTransaction(txns.toList)
      txns.clear()

      // Calling on response function
      val tuple = response(tltxn, state)
      state = tuple._2

      // Converting back to TLChannels
      val responses = TLTransactiontoTLBundles(tuple._1)

      // Writing response(s)
      for (resp <- responses) {
        writeChannel(resp)

        // Clock step called here
        // We won't be getting any requests since A ready is low
        clk.step()
      }
    } else {
      // Step clock even if no response is driven (for burst requests)
      clk.step()
    }
  }

  // Currently just processes the requests from master
  fork {
    reset()
    while (true) {
      process(readA())
    }
  }
}

// General TL Monitor
class TLMonitor(clock: Clock, interface: TLBundle, hasBCE: Boolean = false) extends VerifTLMonitorFunctions {
  val clk = clock
  val TLChannels = interface

  val txns = ListBuffer[TLChannel]()

  def getMonitoredTransactions (filter: TLChannel => Boolean = {(_: TLChannel) => true}) : List[TLTransaction] = {
    val result = new ListBuffer[TLTransaction]
    val filtered = txns.filter(filter)
    for (g <- groupTLBundles(filtered.toList)) {
      result += TLBundlestoTLTransaction(g)
    }
    result.toList
  }

  def clearMonitoredTransactions(): Unit = {
    txns.clear()
  }

  fork.withRegion(Monitor) {
    while (true) {
      if (hasBCE) {
        txns ++= readABCDE()
      } else {
        txns ++= readAD()
      }
      clk.step()
    }
  }
}
