; arcblade.arc
;
; Copyright 2010 Ross Angle
;
; This file is part of JVM-Blade.
;
; JVM-Blade is free software: you can redistribute it and/or modify it
; under the terms of the GNU General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; JVM-Blade is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with JVM-Blade.  If not, see <http://www.gnu.org/licenses/>.


; This is incomplete, untested code. It's also meant to be used with
; Lathe, which can be found at
; <http://www.github.com/rocketnia/lathe>. This code assumes that
; Lathe's "arc" directory will be copied to a folder named "lathe" in
; the same folder as this file (presumably, the Arc installation's
; "libs" directory).

(packed:using-rels-as ut "lathe/utils.arc"


; A contribution of value to sig, expecting reducer to ultimately
; reduce the values. The next parameter is a continuation of the lead.
; Note that reducer and value don't need to be hard values; either of
; them can be a result or a structure containing a result of an
; answer to 'btl-lead-softask or 'btl-calc-softask.
;
; A sig is a list of values representing a path of namespaces.
; Contributing to a sig is the same as contributing a contribution
; object to the sig's parent, where the sig's parent uses a particular
; reducer which creates a namespace full of multivals out of a bunch
; of contribution objects.
;
(def my.btl-lead-contrib (sig reducer value next)
  (list 'btl-lead-contrib sig reducer value next))

; A promise not to contribute to any sig that doesn't satisfy the
; filter. The next parameter is a continuation of the lead.
(def my.btl-lead-promise (filter next)
  (list 'btl-lead-promise filter next))

; A request for a reference to the reduced value of sig. The value
; isn't needed yet, so it can be filled in later using mutation. The
; next parameter is a function that will take the answer and return a
; continuation of the lead.
(def my.btl-lead-softask (sig next)
  (list 'btl-lead-softask sig next))

; A demand for the value of sig to be mutated into the existing soft
; reference to it. The next parameter is a continuation of the lead.
; Unlike 'btl-lead-softask, next doesn't take a parameter.
(def my.btl-lead-hardask (sig next)
  (list 'btl-lead-hardask sig next))

; The result of a lead that has errored out. Note that this has no
; next continuation.
(def my.btl-lead-err (error)
  (list 'btl-lead-err error))

; The result of a lead that has run its course. Note that this has no
; next continuation.
(def my.btl-lead-end ()
  (list 'btl-lead-end))


; To call a reducer or a filter may require reducing some other
; multivals first, so they have the same kinds of soft and hard asking
; capabilities as leads do. However, they return results.

(def my.btl-calc-softask (sig next)
  (list 'btl-calc-softask sig next))

(def my.btl-calc-hardask (sig next)
  (list 'btl-calc-hardask sig next))

(def my.btl-calc-err (error)
  (list 'btl-calc-err error))

(def my.btl-calc-result (value)
  (list 'btl-calc-result value))

; This returns a two-element list containing a calc-result and a
; boolean indicating whether any advancement actually happened. The
; calc-result will be either a btl-calc-result or a btl-calc-hardask.
; However, it will never be a btl-calc-hardask for which get-ref
; already returns a filled reference.
(def my.advance-calc-repeatedly (calc-result get-ref)
  (let ref-is-set [my.blade-ref-is-set-direct do.get-ref._]
    (ut:xloop calc-result calc-result did-anything nil
      (case car.calc-result
        btl-calc-result   (list calc-result did-anything)
        btl-calc-err      (err:+ "A calculation resulted in this "
                                 "error: " cadr.calc-result)
        btl-calc-softask  (let (_ sig nextcalc) calc-result
                            (do.next (my.blade-call nextcalc
                                                    do.get-ref.sig)
                                     t))
        btl-calc-hardask  (let (_ sig nextcalc) calc-result
                            (if do.ref-is-set.sig
                              (do.next my.blade-call.nextcalc t)
                              (list calc-result did-anything)))
                          (err:+ "An unknown calc-result type was "
                                 "encountered.")))))

; This returns a two-element list containing a lead-result and a
; boolean indicating whether any advancement actually happened. The
; lead-result will be either a btl-lead-end, an unsatisfied
; btl-lead-hardask, or a btl-lead-contrib. It will only be a
; btl-lead-contrib if none of the lead's promises reject the sig and
; at least one of them requires an unsatisfied hard ask.
;
; The add-contrib parameter should be a function with side effects
; that takes a sig, a reducer, and a contributed value. It shouldn't
; test the contribution against the lead's promises; this takes care
; of that step already.
;
(def my.advance-lead-repeatedly (lead-result get-ref add-contrib
                                 add-promise get-promises)
  (let ref-is-set [my.blade-ref-is-set-direct do.get-ref._]
    (ut:xloop lead-result lead-result did-anything nil
      (case car.lead-result
        btl-lead-end      (list lead-result did-anything)
        btl-lead-err      (err:+ "A lead resulted in this error: "
                                 cadr.lead-result)
        btl-lead-softask  (let (_ sig nextlead) lead-result
                            (do.next (my.blade-call nextlead
                                                    do.get-ref.sig)
                                     t))
        btl-lead-hardask  (let (_ sig nextlead) lead-result
                            (if do.ref-is-set.sig
                              (do.next my.blade-call.nextlead t)
                              (list lead-result did-anything)))
        btl-lead-contrib
          (with ((_ sig reducer value nextlead) lead-result
                 any-asks nil)
            (each filter call.get-promises
              (let (result-type parm) (my.advance-calc-repeatedly
                                        (my.blade-call-tl filter sig)
                                        get-ref)
                (case result-type btl-calc-hardask
                  (= any-asks t)
                  (unless my.blade-truthy.parm
                    (err:+ "A lead broke a promise not to contribute "
                           "to this sig: " sig)))))
            (if any-asks
              (list lead-result did-anything)
              (do (do.add-contrib sig reducer value)
                  (do.next my.blade-call.nextlead t))))
        btl-lead-promise  (let (_ filter nextlead) lead-result
                            do.add-promise.filter
                            (do.next my.blade-call.nextlead t))
                          (err:+ "An unknown lead-result type was "
                                 "encountered.")))))

; TODO: See if this is sufficient.
(def my.blade-truthy (blade-val)
  blade-val)

; TODO: See if this is sufficient. It probably isn't, considering that
; one may be a ref pointing to the other or that they both may be refs
; pointing to the same thing.
(def my.blade-reducers-are-equivalent (a b)
  (iso a b))

; Call a Blade function which yields calc-results.
; TODO: Actually implement this, rather than just pretending to.
(def my.blade-call-tl (blade-func . parms)
  (apply blade-func parms))

; TODO: Actually implement this, rather than just pretending to.
(def my.blade-call (blade-func . parms)
  (apply blade-func parms))

(def my.blade-make-ref (sig)
  (annotate 'blade-tl-ref list.sig))

(def my.blade-set-ref (ref val)
  (let rep-ref rep.ref
    (when cdr.rep-ref
      (err "A reference sent to blade-set-ref was already set."))
    (while (and (isa val 'blade-tl-ref) (cdr rep.val))
      (= val (car rep.val)))
    (when (is ref val)
      (err "A reference can't be set to itself."))
    (= car.rep-ref val cdr.rep-ref t)))))

(def my.blade-deref-soft (ref)
  (case type.ref blade-tl-ref
    (withs (rep-ref rep.ref
            (sig-or-val . set-already) rep-ref)
      (if set-already
        (case type.sig-or-val blade-tl-ref
          (let (val-sig-or-val . val-set-already) rep.sig-or-val
            (if val-set-already
              (= car.rep-ref my.blade-deref-soft.val-sig-or-val)
              sig-or-val))
          sig-or-val)
        ref))
    ref))

(def my.blade-ref-is-set-indirect (ref)
  (~isa my.blade-deref-soft.ref 'blade-tl-ref))

(def my.blade-ref-is-set-direct (ref)
  (case type.ref blade-tl-ref (cdr rep.ref)))

; This is the only reducer which isn't a function. It's a special
; case.
(= my.namespace-reducer (uniq))

; TODO: See if this is sufficient.
(def my.sig-iso (a b)
  (iso a b))

(def my.sig-ancestors (sig)
  ut.tails.sig)

(def my.sig-is-parent (parent child)
  (and acons.parent (my.sig-iso cdr.parent child)))

(def my.sig-derivative (sig)
  car.sig)

; TODO: See if this is the only needed base.
(wipe my.sig-base)

(def my.make-namespace (tablist-of-singletons)
  (annotate 'blade-tl-namespace tablist-of-singletons))

(def my.sigtab ()
  (list nil))

(def my.sigtab-ref (sigtab key)
  (whenlet (key val) (find [my.sig-iso key car._] car.sigtab)
    val))

(def my.sigtab-len (sigtab)
  (len car.sigtab))

(def my.sigtab-keys (sigtab)
  (map car car.sigtab))

(def my.sigtab-set (sigtab key value)
  (if value
    (iflet assoc (find [my.sig-iso key car._] car.sigtab)
      (= cadr.assoc value)
      (zap [cons (list key value) _] car.sigtab))
    (zap [rem [my.sig-iso key car._] _] car.sigtab))
  value)

(mac my.fn-sigtab-or= (sigtab key fn-alternative)
  (or (my.sigtab-ref sigtab key)
      (my.sigtab-set sigtab key call.fn-alternative)))

(mac my.sigtab-or= (sigtab key alternative)
  `(,my!fn-sigtab-or= ,sigtab ,key (fn () ,alternative)))

(def my.sigtab-push (sigtab key elem)
  (iflet assoc (find [my.sig-iso key car._] car.sigtab)
    (push elem cadr.assoc)
    (push (list key list.elem) car.sigtab)))

(def my.make-blade-set (lst)
  (annotate 'blade-set lst))

; This takes a bunch of initial lead-result values, follows those
; leads, and returns the reduced value associated with the empty sig,
; which usually turns out to be a Blade namespace. Even if the return
; value can be determined early, the leads will still be followed to
; their conclusions so that promise breaking can be detected, and
; while those are being looked for, a dependency loop may be detected
; instead.
(def my.blade-toplevel (initial-lead-results)
  (withs (lead-infos (map list initial-lead-results)
          refs (my.sigtab)
          reductions (my.sigtab)
          reducers (my.sigtab)
          contribs (my.sigtab)
          get-ref (fn (sig)
                    (or (my.sigtab-ref refs sig)
                        (do (each ancestor (cdr my.sig-ancestors.sig)
                              (my:sigtab-or= refs ancestor
                                my.blade-make-ref.sig))
                            (my.sigtab-set refs sig
                              my.blade-make-ref.sig))))
          ref-is-set [my.blade-ref-is-set-direct do.get-ref._]
          set-ref (fn (sig val) (my.blade-set-ref do.get-ref.sig val))
          add-contrib (fn (sig reducer value)
                        (when (my.blade-reducers-are-equivalent
                                reducer namespace-reducer)
                          (err:+ "A contribution was made using the "
                                 "namespace reducer directly."))
                        (each ancestor (cdr my.sig-ancestors.sig)
                          (iflet existing-reducer
                                   (my.sigtab-ref reducers ancestor)
                            (unless (my.blade-reducers-are-equivalent
                                      existing-reducer
                                      namespace-reducer)
                              (err "A reducer conflict occurred."))
                            (my.sigtab-set reducers ancestor
                              namespace-reducer)))
                        (iflet existing-reducer
                                 (my.sigtab-ref reducers sig)
                          (unless (my.blade-reducers-are-equivalent
                                    existing-reducer reducer)
                            (err "A reducer conflict occurred."))
                          (my.sigtab-set reducers sig reducer))
                        (my.sigtab-push contribs sig value))
          promise-rejects-1 (fn (filter sig)
                              (let ((result-type parm)
                                    did-anything)
                                     (my.advance-calc-repeatedly
                                       (my.blade-call-tl filter sig)
                                       get-ref)
                                (case result-type btl-calc-result
                                  (no my.blade-truthy.parm))))
          promise-rejects (fn (filter sig)
                            (some [do.promise-rejects-1 filter _]
                                  my.sig-ancestors.sig))
          advance-lead (fn (lead-info)
                         (let (new-lead-result did-anything)
                                (my.advance-lead-repeatedly
                                  car.lead-info
                                  get-ref
                                  add-contrib
                                  [push _ cdr.lead-info]
                                  (fn () cdr.lead-info))
                           (= car.lead-info new-lead-result)
                           did-anything))
          advance-reduction (fn (sig)
                              (let ((result-type parm) did-anything)
                                     (my.advance-calc-repeatedly
                                       (my.sigtab-ref reductions sig)
                                       get-ref)
                                (case result-type btl-calc-result
                                  (do (my.sigtab-set reductions sig
                                        nil)
                                      (do.set-ref sig parm)
                                      t)
                                  did-anything))))
    (do.get-ref my.sig-base)
    (catch:while t
      (let did-anything nil
        (each lead-info lead-infos
          (when do.advance-lead.lead-info
            (= did-anything t)))
        (each sig my.sigtab-keys.reductions
          (when do.advance-reduction.sig
            (= did-anything t)))
        (let old-len (or did-anything len.lead-infos)
          (zap [rem [is caar._ 'btl-lead-end] _] lead-infos)
          (or= did-anything (~is len.lead-infos old-len)))
        (let old-len (or did-anything my.sigtab-len.refs)
          (each sig my.sigtab-keys.refs
            (when (and (my.sigtab-ref reducers sig)
                       (no (my.sigtab-ref reductions sig))
                       (~ref-is-set sig)
                       (all [some [do.promise-rejects _ sig] cdr._]
                            lead-infos))
              (let reducer (my.sigtab-ref reducers sig)
                (if (my.blade-reducers-are-equivalent
                      reducer namespace-reducer)
                  (let kids (keep [my.sig-is-parent sig _]
                                  my.sigtab-keys.refs)
                    (when (all ref-is-set kids)
                      (do.set-ref sig
                        (my:make-namespace:ut:maplet kid kids
                          (list my.sig-derivative.kid
                                (list (my.blade-deref-soft
                                        do.get-ref.kid)))))
                      (= did-anything t)))
                  (do (my.sigtab-set reductions sig
                        (my.blade-call-tl reducer
                          (my:make-blade-set:my:sigtab-ref contribs
                                                           sig)))
                      (= did-anything t))))))
          (or= did-anything (~is my.sigtab-len.refs old-len)))
        (unless (or lead-infos (~all ref-is-set my.sigtab-keys.refs))
          (throw (my.blade-deref-soft (do.get-ref my.sig-base))))
        (unless did-anything (err "There was a dependency loop."))))


)
