package android.emu6502.instructions.impl

import android.emu6502.CPU
import android.emu6502.instructions.BaseInstruction
import android.emu6502.instructions.Instruction

/** CLear Carry */
class CLC(private val cpu: CPU) : BaseInstruction(Instruction.CLC, cpu) {
  override fun single() {
    cpu.CLC()
  }
}

