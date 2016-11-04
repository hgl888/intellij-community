/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import java.io.DataInput
import java.io.DataOutput

/**
 * @author peter
 */
internal object MethodDataExternalizer : DataExternalizer<MethodData> {

  override fun save(out: DataOutput, data: MethodData) {
    writeNullable(out, data.nullity) { writeNullity(out, it) }
    writeNullable(out, data.purity) { writePurity(out, it) }
    writeList(out, data.contracts) { writeContract(out, it) }
    DataInputOutputUtil.writeINT(out, data.bodyStart)
    DataInputOutputUtil.writeINT(out, data.bodyEnd)
  }

  override fun read(input: DataInput): MethodData {
    val nullity = readNullable(input) { readNullity(input) }
    val purity = readNullable(input) { readPurity(input) }
    val contracts = readList(input) { readContract(input) }
    return MethodData(nullity, purity, contracts, DataInputOutputUtil.readINT(input), DataInputOutputUtil.readINT(input))
  }

  private fun writeNullity(out: DataOutput, nullity: NullityInferenceResult) = when (nullity) {
    is NullityInferenceResult.Predefined -> { out.writeByte(0); out.writeByte(nullity.value.ordinal) }
    is NullityInferenceResult.FromDelegate -> { out.writeByte(1); writeRanges(out, nullity.delegateCalls) }
    else -> throw IllegalArgumentException(nullity.toString())
  }
  private fun readNullity(input: DataInput): NullityInferenceResult = when (input.readByte().toInt()) {
    0 -> NullityInferenceResult.Predefined(Nullness.values()[input.readByte().toInt()])
    else -> NullityInferenceResult.FromDelegate(readRanges(input))
  }

  private fun writeRanges(out: DataOutput, ranges: List<ExpressionRange>) = writeList(out, ranges) { writeRange(out, it) }
  private fun readRanges(input: DataInput) = readList(input) { readRange(input) }

  private fun writeRange(out: DataOutput, range: ExpressionRange) {
    DataInputOutputUtil.writeINT(out, range.startOffset)
    DataInputOutputUtil.writeINT(out, range.endOffset)
  }
  private fun readRange(input: DataInput) = ExpressionRange(DataInputOutputUtil.readINT(input), DataInputOutputUtil.readINT(input))

  private fun writePurity(out: DataOutput, purity: PurityInferenceResult) {
    writeRanges(out, purity.mutatedRefs)
    writeNullable(out, purity.singleCall) { writeRange(out, it) }
  }
  private fun readPurity(input: DataInput) = PurityInferenceResult(readRanges(input), readNullable(input) { readRange(input) })

  private fun writeContract(out: DataOutput, contract: PreContract): Unit = when (contract) {
    is DelegationContract -> { out.writeByte(0); writeRange(out, contract.expression); out.writeBoolean(contract.negated) }
    is KnownContract -> { out.writeByte(1);
      writeContractArguments(out, contract.contract.arguments.toList())
      out.writeByte(contract.contract.returnValue.ordinal)
    }
    is MethodCallContract -> { out.writeByte(2);
      writeRange(out, contract.call);
      writeList(out, contract.states) { writeContractArguments(out, it) }
    }
    is NegatingContract -> { out.writeByte(3); writeContract(out, contract.negated) }
    is SideEffectFilter -> { out.writeByte(4);
      writeRanges(out, contract.expressionsToCheck)
      writeList(out, contract.contracts) { writeContract(out, it) }
    }
    else -> throw IllegalArgumentException(contract.toString())
  }
  private fun readContract(input: DataInput): PreContract = when (input.readByte().toInt()) {
    0 -> DelegationContract(readRange(input), input.readBoolean())
    1 -> KnownContract(MethodContract(readContractArguments(input).toTypedArray(), readValueConstraint(input)))
    2 -> MethodCallContract(readRange(input), readList(input) { readContractArguments(input) })
    3 -> NegatingContract(readContract(input))
    else -> SideEffectFilter(readRanges(input), readList(input) { readContract(input) })
  }

  private fun writeContractArguments(out: DataOutput, arguments: List<MethodContract.ValueConstraint>) =
      writeList(out, arguments) { out.writeByte(it.ordinal) }
  private fun readContractArguments(input: DataInput) = readList(input, { readValueConstraint(input) })

  private fun readValueConstraint(input: DataInput) = MethodContract.ValueConstraint.values()[input.readByte().toInt()]

}

// utils

private fun <T> writeNullable(out: DataOutput, value: T?, writeItem: (T) -> Unit) = when (value) {
  null -> out.writeBoolean(false)
  else -> { out.writeBoolean(true); writeItem(value) }
}
private fun <T> readNullable(input: DataInput, readEach: () -> T): T? = if (input.readBoolean()) readEach() else null

private fun <T> writeList(out: DataOutput, list: List<T>, writeEach: (T) -> Unit) {
  DataInputOutputUtil.writeINT(out, list.size)
  list.forEach(writeEach)
}
private fun <T> readList(input: DataInput, readEach: () -> T) = (0 until DataInputOutputUtil.readINT(input)).map { readEach() }
