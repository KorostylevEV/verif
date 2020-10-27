//******************************************************************************
// Copyright (c) 2018 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package verif

import org.scalatest._

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._

/**
 * Factory object to help create a set of BOOM parameters to use in tests
 */
object VerifTestUtils {

  private def augment(tp: TileParams)(implicit p: Parameters): Parameters = p.alterPartial {
    case TileKey => tp

    case TileVisibilityNodeKey => TLEphemeralNode()(ValName("tile_master"))

    case LookupByHartId => lookupByHartId(Seq(tp))
  }

  private def lookupByHartId(tps: Seq[TileParams]) = {
    // return a new lookup hart
    new LookupByHartIdImpl {
      def apply[T <: Data](f: TileParams => Option[T], hartId: UInt): T =
        PriorityMux(tps.collect { case t if f(t).isDefined => (t.hartId.U === hartId) -> f(t).get })
    }
  }

  def getVerifParameters(configName: String, configPackage: String = "verif"): Parameters = {
    // get the full path to the config
    val fullConfigName = configPackage + "." + configName

    // get the default unmodified params
    val origParams: Parameters = try {
      (Class.forName(fullConfigName).newInstance.asInstanceOf[Config] ++ Parameters.empty)
    }
    catch {
      case e: java.lang.ClassNotFoundException =>
        throw new Exception(s"""Unable to find config "$fullConfigName".""", e)
    }

    // get the tile parameters
    val verifTileParams = origParams(TilesLocated(InSubsystem)) // this is a seq
    //verifTileParams(0).instantiate(origParams) -> ResourceBinding must be called from within a BindingScope

    // augment the parameters
    val outParams = augment(verifTileParams(0).tileParams)(origParams)

    //orgParams
    outParams
  }
}
