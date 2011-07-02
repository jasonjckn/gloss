;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  gloss.data.primitives
  (:use
    [gloss.data bytes]
    [gloss.core protocols])
  (:import
    [java.nio
     Buffer
     ByteBuffer]))

(defn has-bytes [n buf-seq]
  (< (.remaining ^Buffer (first buf-seq)) n))

(defn to-byte [x]
  (cond
    (number? x) (byte x)
    (char? x) (-> x int byte)
    (string? x) (-> x first int byte)
    :else (throw (Exception. (str "Cannot convert " x " to byte.")))))

(defmacro primitive-codec [accessor writer size typecast transform-fn
                           r-transform-fn]
  `(reify
     Reader
     (read-bytes [this# b#]
       (if (< (byte-count b#) ~size)
	 [false this# b#]
	 (let [^ByteBuffer first-buf# (first b#)
	       remaining# (.remaining ^Buffer first-buf#)]
           (.order first-buf# (java.nio.ByteOrder/LITTLE_ENDIAN))
	   (cond
	     (= ~size remaining#)
	     [true
	      (~r-transform-fn (~accessor ^ByteBuffer first-buf#))
	      (rest b#)]
	     
	     (< ~size remaining#)
	     [true
	      (~r-transform-fn (~accessor ^ByteBuffer first-buf#))
	      (-> b# rewind-bytes (drop-bytes ~size))]
	     
	     :else
	     (let [buf# (take-contiguous-bytes b# ~size)]
	       [true
		(~r-transform-fn (~accessor ^ByteBuffer buf#))
		(drop-bytes b# ~size)])))))
     Writer
     (sizeof [_]
       ~size)
     (write-bytes [_ buf# v#]
       (with-buffer [buf# ~size]
	 (~writer ^ByteBuffer buf# (~typecast (~transform-fn v#)))))))

(defn mk-unsigned [bit-size n]
  (if (< n 0)
    (+ (long (Math/pow 2 bit-size)) n)
    n))

(def primitive-codecs
  {:byte (primitive-codec .get .put 1 byte to-byte identity) 
   :ubyte (primitive-codec .get .put 1 short to-byte (partial mk-unsigned 8)) 
   :int16 (primitive-codec .getShort .putShort 2 short identity identity)
   :uint16 (primitive-codec .getShort .putShort 2 short identity (partial mk-unsigned 16))
   :int32 (primitive-codec .getInt .putInt 4 int identity identity)
   :uint32 (primitive-codec .getInt .putInt 4 long identity (partial mk-unsigned 32))
   :int64 (primitive-codec .getLong .putLong 8 long identity identity)
   :float32 (primitive-codec .getFloat .putFloat 4 float identity identity)
   :float64 (primitive-codec .getDouble .putDouble 8 double identity identity)})

