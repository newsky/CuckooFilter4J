/*
 *MIT License
 *
 *Copyright (c) 2016 Mark Gunlogson
 *
 *Permission is hereby granted, free of charge, to any person obtaining a copy
 *of this software and associated documentation files (the "Software"), to deal
 *in the Software without restriction, including without limitation the rights
 *to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *copies of the Software, and to permit persons to whom the Software is
 *furnished to do so, subject to the following conditions:
 *
 *The above copyright notice and this permission notice shall be included in all
 *copies or substantial portions of the Software.
 *
 *THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *SOFTWARE.
*/

package cuckooforjava;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.IntMath;
import com.google.common.math.LongMath;

class FilterTable implements Serializable {
	/**
	 * NOTE: for speed, we don't check for inserts into invalid bucket indexes or bucket positions!
	 */
	private static final long serialVersionUID = 4172048932165857538L;
	/*
	 * NOTE: Google's Guava library uses a custom BitSet implementation that
	 * looks to be adapted from the Lucene project. Guava project notes show
	 * this seems to be done for faster serialization and support for
	 * longs(giant filters) We use regular BitSet for now, since cuckoo filters
	 * can't be combined the need for serialization is lower and I would rather
	 * have readability.
	 */
	private final BitSet memBlock;
	private final int bitsPerTag;
	private final Random rando;
	private int maxKeys;

	private FilterTable(BitSet memBlock, int bitsPerTag, int maxKeys) {
		this.bitsPerTag = bitsPerTag;
		this.memBlock = memBlock;
		this.rando = new Random();
		this.maxKeys = maxKeys;
	}

	public static FilterTable create(int bitsPerTag, int numBuckets, int maxKeys) {
		checkArgument(bitsPerTag < 28, "tagBits (%s) must be < 28", bitsPerTag);
		//shorter fingerprints don't give us a good fill capacity
		checkArgument(bitsPerTag > 4, "tagBits (%s) must be > 4", bitsPerTag);
		checkArgument(numBuckets > 1, "numBuckets (%s) must be > 1", numBuckets);
		checkArgument(maxKeys > 1, "maxKeys (%s) must be > 1", maxKeys);
		// checked so our implementors don't get too.... "enthusiastic" with
		// table size
		long bitsPerBucket = IntMath.checkedMultiply(CuckooFilter.BUCKET_SIZE, bitsPerTag);
		long estBitSetSize = LongMath.checkedMultiply(bitsPerBucket, (long) numBuckets);
		checkArgument(estBitSetSize < (long)Integer.MAX_VALUE, "Initialized BitSet too large, exceeds 32 bit boundary",
				estBitSetSize);
		BitSet memBlock = new BitSet((int) estBitSetSize);
		return new FilterTable(memBlock, bitsPerTag, maxKeys);
	}

	class TagResult {
		private final boolean success;
		private final int oldTag;

		public TagResult(boolean success, int oldTag) {
			this.success = success;
			// oldTag is only set if we kicked another tag out
			this.oldTag = oldTag;
		}

		public boolean isSuccess() {
			return success;
		}

		public int getOldTag() {
			return oldTag;
		}

	}

	
	public TagResult insertTagIntoBucket(int bucketIndex, int tag, boolean kickOnFull) {

		for (int i = 0; i < CuckooFilter.BUCKET_SIZE; i++) {
			if (readTag(bucketIndex, i) == 0) {
				writeTag(bucketIndex, i, tag);
				return new TagResult(true, 0);
			}
		}
		if (kickOnFull) {
			// only get here if bucket is full :(
			// pick a random victim to kick from bucket if full
			// insert our new item and hand back kicked one
			int randomBucketPosition = rando.nextInt(CuckooFilter.BUCKET_SIZE);
			int oldTag = readTag(bucketIndex, randomBucketPosition);
			assert oldTag != 0;// should only get here if bucket is full...so no
								// zeros
			writeTag(bucketIndex, randomBucketPosition, tag);
			return new TagResult(true, oldTag);
		}
		return new TagResult(false, 0);
	}

	public boolean findTagInBuckets(int bucketIndex1, int bucketIndex2, int tag) {
		for (int i = 0; i < CuckooFilter.BUCKET_SIZE; i++) {
			if ((readTag(bucketIndex1, i) == tag) || (readTag(bucketIndex2, i) == tag))
				return true;
		}
		return false;
	}

	public int getStorageSize() {
		return memBlock.size();
	}

	public boolean deleteTagInBucket(int bucketIndex, int tag) {
		for (int i = 0; i < CuckooFilter.BUCKET_SIZE; i++) {
			if (readTag(bucketIndex, i) == tag) {
				writeTag(bucketIndex, i, 0);
				return true;
			}
		}
		return false;
	}
@VisibleForTesting
	int readTag(int bucketIndex, int posInBucket) {
		int tagStartIdx = getTagOffset(bucketIndex, posInBucket);
		int tag = 0;
		int tagEndIdx = tagStartIdx + bitsPerTag;
		// looping over true bits per nextBitSet javadocs
		for (int i = memBlock.nextSetBit(tagStartIdx); i >= 0 && i < tagEndIdx; i = memBlock.nextSetBit(i + 1)) {
			// set corresponding bit in tag
			tag |= 1 << (i-tagStartIdx);
		}
		return tag;
	}
@VisibleForTesting
	void writeTag(int bucketIndex, int posInBucket, int tag) {
		int tagStartIdx = getTagOffset(bucketIndex, posInBucket);
		// BIT BANGIN YEAAAARRHHHGGGHHH
		for (int i = 0; i < bitsPerTag; i++) {
			// second arg just does bit test in tag
			memBlock.set(tagStartIdx + i, (tag & (1L << i)) != 0);
		}
	}

	private int getTagOffset(int bucketIndex, int posInBucket) {
		return (bucketIndex * CuckooFilter.BUCKET_SIZE * bitsPerTag) + (posInBucket*bitsPerTag);
	}

	@Override
	public boolean equals(@Nullable Object object) {
		if (object == this) {
			return true;
		}
		if (object instanceof FilterTable) {
			FilterTable that = (FilterTable) object;
			return this.bitsPerTag == that.bitsPerTag && this.memBlock.equals(that.memBlock)
					 && this.maxKeys == that.maxKeys;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(bitsPerTag, memBlock, maxKeys);
	}

	public FilterTable copy() {
		return new FilterTable((BitSet) memBlock.clone(), bitsPerTag,  maxKeys);
	}

}