package android.emu6502

import android.emu6502.instructions.BaseInstruction
import android.emu6502.instructions.Instruction
import android.emu6502.instructions.InstructionTarget
import android.emu6502.instructions.Opcodes
import android.emu6502.instructions.impl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.*
import java.util.concurrent.CountDownLatch

class CPU(val memory: Memory) : Display.Callbacks {
  private val bgHandlerThread = HandlerThread("Screencast Thread")
  private val bgHandler: Handler
  private var executionLock: CountDownLatch? = null

  init {
    bgHandlerThread.start()
    bgHandler = Handler(bgHandlerThread.looper)
  }

  var A: Int = 0      // Accumulator
  var X: Int = 0      // Register X
  var Y: Int = 0      // Register Y
  var PC: Int = 0x600 // Program counter
  var SP: Int = 0xFF  // Stack pointer
  var P: Int = 0x30   // Processor flags

  private var isRunning = false
  private var debug = false
  private var monitoring = false
  private var TAG = "CPU"
  private val instructionList: HashMap<Int, InstructionTarget> = HashMap()
  private val operationList: HashMap<Instruction, BaseInstruction> = hashMapOf(
      Pair(Instruction.ADC, ADC(this)), Pair(Instruction.AND, AND(this)),
      Pair(Instruction.ASL, ASL(this)), Pair(Instruction.BIT, BIT(this)),
      Pair(Instruction.LDA, LDA(this)), Pair(Instruction.LDX, LDX(this)),
      Pair(Instruction.LDY, LDY(this)), Pair(Instruction.STA, STA(memory, this)),
      Pair(Instruction.STX, STX(this)), Pair(Instruction.TAX, TAX(this)),
      Pair(Instruction.INX, INX(this)), Pair(Instruction.DEX, DEX(this)),
      Pair(Instruction.ORA, ORA(this)), Pair(Instruction.CPX, CPX(this)),
      Pair(Instruction.BRK, BRK(this)), Pair(Instruction.BNE, BNE(this)),
      Pair(Instruction.JMP, JMP(this)), Pair(Instruction.JSR, JSR(this)),
      Pair(Instruction.RTS, RTS(this)), Pair(Instruction.SEI, SEI(this)),
      Pair(Instruction.DEY, DEY(this)), Pair(Instruction.CLC, CLC(this)),
      Pair(Instruction.CMP, CMP(this)), Pair(Instruction.BEQ, BEQ(this)),
      Pair(Instruction.TXA, TXA(this)), Pair(Instruction.BPL, BPL(this)),
      Pair(Instruction.LSR, LSR(this)), Pair(Instruction.BCS, BCS(this)),
      Pair(Instruction.INC, INC(this)), Pair(Instruction.NOP, NOP(this)),
      Pair(Instruction.SEC, SEC(this)), Pair(Instruction.SBC, SBC(this)),
      Pair(Instruction.BCC, BCC(this)), Pair(Instruction.DEC, DEC(this))
      //Pair(Instruction.BMI, BMI(this)), Pair(Instruction.BVC, BVC(this)),
      //Pair(Instruction.BVS, BVS(this)), Pair(Instruction.CPY, CPY(this)),
      //Pair(Instruction.EOR, EOR(this)), Pair(Instruction.CLI, CLI(this)),
      //Pair(Instruction.CLV, CLV(this)), Pair(Instruction.CLD, CLD(this)),
      //Pair(Instruction.SED, SED(this)), Pair(Instruction.TAY, TAY(this)),
      //Pair(Instruction.TYA, TYA(this)), Pair(Instruction.INY, INY(this)),
      //Pair(Instruction.ROR, ROR(this)), Pair(Instruction.ROL, ROL(this)),
      //Pair(Instruction.RTI, RTI(this)), Pair(Instruction.TXS, TXS(this)),
      //Pair(Instruction.TSX, TSX(this)), Pair(Instruction.PHA, PHA(this)),
      //Pair(Instruction.PLA, PLA(this)), Pair(Instruction.PHP, PHP(this)),
      //Pair(Instruction.PLP, PLP(this)), Pair(Instruction.STY, STY(this))
  )

  // for testing only
  fun testRun() {
    isRunning = true
    while (true) {
      setRandomByte()
      executeNextInstruction()
      if (PC == 0 || !isRunning) {
        break
      }
    }
    stop()
  }

  fun run() {
    isRunning = true
    bgHandler.post { innerRun() }
  }

  private fun innerRun() {
    (0..97).forEach { execute() }
    bgHandler.postDelayed({ innerRun() }, 10)
  }

  private fun execute() {
    if (!isRunning) {
      return
    }

    setRandomByte()
    executeNextInstruction()

    if (PC == 0 || !isRunning) {
      stop()
      Log.i(TAG, "Program end at PC=$" + (PC - 1).toHexString() + ", A=$" + A.toHexString() +
          ", X=$" + X.toHexString() + ", Y=$" + Y.toHexString())
    }
  }

  private fun executeNextInstruction() {
    val instruction = popByte()
    val target = instructionList[instruction]
    if (target != null) {
      target.method.invoke()
    } else {
      val candidate = Opcodes.MAP.entries
          .first { e -> e.value.any { it.opcode == instruction } }
      throw Exception(
          "Address $${PC.toHexString()} - unknown opcode 0x${instruction.toHexString()} " +
              "(instruction ${candidate.key.name})")
    }
  }

  fun stop() {
    isRunning = false
    bgHandler.removeCallbacks(null)
  }

  fun reset() {
    var i = 0
    while (i < 0x600) {
      memory.set(i++, 0x00)
    }
    A = 0
    Y = 0
    X = 0
    PC = 0x600
    SP = 0xff
    P = 0x30
  }

  fun addInstruction(opcode: Int, target: InstructionTarget) {
    instructionList.put(opcode, target)
  }

  fun popByte(): Int {
    return memory.get(PC++).and(0xff)
  }

  private fun setRandomByte() {
    memory.set(0xfe, Math.floor(Math.random() * 256).toInt())
  }

  fun setSZFlagsForRegA() {
    setSVFlagsForValue(A)
  }

  fun setSZflagsForRegX() {
    setSVFlagsForValue(X)
  }

  fun setSZflagsForRegY() {
    setSVFlagsForValue(Y)
  }

  fun setSVFlagsForValue(value: Int) {
    if (value != 0) {
      P = P.and(0xfd)
    } else {
      P = P.or(0x02)
    }
    if (value.and(0x80) != 0) {
      P = P.or(0x80)
    } else {
      P = P.and(0x7f)
    }
  }

  fun popWord(): Int {
    return popByte() + popByte().shl(8)
  }

  fun overflow(): Boolean {
    return P.and(0x40) != 0
  }

  fun decimalMode(): Boolean {
    return P.and(8) != 0
  }

  fun carry(): Boolean {
    return P.and(1) != 0
  }

  fun negative(): Boolean {
    return P.and(0x80) != 0
  }

  fun zero(): Boolean {
    return P.and(0x02) != 0
  }

  fun setCarryFlagFromBit0(value: Int) {
    P = P.and(0xfe).or(value.and(1))
  }

  fun jumpBranch(offset: Int) {
    if (offset > 0x7f) {
      PC -= (0x100 - offset)
    } else {
      PC += offset
    }
  }

  fun doCompare(reg: Int, value: Int) {
    if (reg >= value) {
      SEC()
    } else {
      CLC()
    }
    setSVFlagsForValue(reg - value)
  }

  /** CLear Carry */
  fun CLC() {
    P = P.and(0xfe)
  }

  /** SEt Carry */
  fun SEC() {
    P = P.or(1)
  }

  /** CLear oVerflow */
  fun CLV() {
    P = P.and(0xbf)
  }

  fun setOverflow() {
    P = P.or(0x40)
  }

  fun stackPush(value: Int) {
    memory.set(SP.and(0xff) + 0x100, value.and(0xff))
    SP--
    if (SP < 0) {
      SP = SP.and(0xff)
      Log.i(TAG, "6502 Stack filled! Wrapping...")
    }
  }

  fun stackPop(): Int {
    SP++
    if (SP >= 0x100) {
      SP = SP.and(0xff)
      Log.i(TAG, "6502 Stack emptied! Wrapping...")
    }
    return memory.get(SP + 0x100)
  }

  /**
   * http://nesdev.com/6502.txt
   * Returns the processor flags in the format SV-BDIZC
   * Sign - this is set if the result of an operation is negative, cleared if positive.
   * Overflow - when an arithmetic operation produces a result too large to be represented in a byte
   * Unused - Supposed to be logical 1 at all times.
   * Break - this is set when a software interrupt (BRK instruction) is executed.
   * Decimal Mode - When set, and an Add with Carry or Subtract with Carry instruction is executed,
   *  the source values are treated as valid BCD (Binary Coded Decimal, eg. 0x00-0x99 = 0-99) numbers.
   *  The result generated is also a BCD number.
   * Interrupt - If it is set, interrupts are disabled. If it is cleared, interrupts are enabled.
   * Zero - this is set to 1 when any arithmetic or logical operation produces a zero result, and is
   *  set to 0 if the result is non-zero.
   * Carry - this holds the carry out of the most significant bit in any arithmetic operation.
   *  In subtraction operations however, this flag is cleared - set to 0 - if a borrow is required,
   *  set to 1 - if no borrow is required. The carry flag is also used in shift and rotate logical
   *  operations.
   * */
  fun flags(): String {
    val flags = StringBuilder()
    for (i in 7 downTo 0) {
      flags.append(P.shr(i).and(1))
    }
    return flags.toString()
  }

  override fun onUpdate() {
    executionLock = CountDownLatch(1)
    executionLock?.await()
  }

  override fun onDraw() {
    executionLock?.countDown()
  }

  fun triggerIRQ() {
    throw NotImplementedError()
  }
}