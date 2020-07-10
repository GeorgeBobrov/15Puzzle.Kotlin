package com.example.k15puzzle

import android.animation.*
import android.animation.Animator.AnimatorListener
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.math.roundToLong
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

	enum class TMode {
		Game, GameOver, JustShuffled, PuzzleMatched
	}

	var Base = 0
	var Mode: TMode? = null

	var Tiles = arrayOfNulls<Button?>(0)
	var TileSize: Long = 0
	var TileSpacing: Long = 0
	var SpaceX: Long = 0
	var SpaceY: Long = 0

	val TileFillNormalColor1 = 0xFFFFE4C4.toInt() //bisque
	val TileFillNormalColor2 = 0xFFABE024.toInt()

	var LastResizeTime: Long = 0
	var LastTapTime: Long = 0
	var ClosingAnimation = false
	var TimeRemaining = 0
	var PanelDebugMaximumHeight = 0
	var ResizeCount = 0

	var TimerTime = Handler()
	var TimerResize = Handler()
	var TimerCreateTiles = Handler()
	var TimerTimeRunnable = Runnable { TimerTimeTimer() }
	var TimerResizeRunnable = Runnable { TimerResizeTimer() }
	var TimerCreateTilesRunnable = Runnable { TimerCreateTilesTimer() }

	private val RandomGen = Random()

	val MaxMoveAniDuration = 150f
	val MinMoveAniDuration = 1f

//	var TileClickListener =
//		View.OnClickListener { sender -> OnTilePressed(sender) }

	var TileTouchListener = OnTouchListener { sender, event ->
		if (event.action == MotionEvent.ACTION_DOWN) {
			OnTilePressed(sender)
			return@OnTouchListener true
		}
		false
	}

	var linear: TimeInterpolator = LinearInterpolator()
	var inBack: TimeInterpolator = PathInterpolator(0.6f, -0.28f, 0.735f, 0.045f)
	var outBack: TimeInterpolator = PathInterpolator(0.175f, 0.885f, 0.32f, 1.275f)
	var outExpo: TimeInterpolator = PathInterpolator(0.19f, 1f, 0.22f, 1f)

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		LastResizeTime =	System.currentTimeMillis() //To prevent resize on start on Android

		PanelClient.viewTreeObserver.addOnGlobalLayoutListener { PanelClientResize() }

//		LinearGradient linearGradient = new LinearGradient();

		SetBase(4)
	}


	fun SetMode(Value: TMode)
	{
		Mode = Value
		if (Mode == TMode.Game)
			TimerTime.postDelayed(TimerTimeRunnable, 1000)
		else
			TimerTime.removeCallbacks(TimerTimeRunnable)
	}


	fun ButtonBaseOnClick(sender: View)
	{
		val SenderButton = sender as Button
		val LBase = SenderButton.text[0].toString().toInt()
		SetBase(LBase)
	}

	fun SetBase(Value: Int)
	{
		if (Value == Base)
		{
			AnimateBaseNotChanged()
			return
		}
		SetMode(TMode.GameOver)
		AnimateTilesDisappeare()
		Base = Value
		SetMaxTime()
		val delay = if (Tiles.size > 0) (520 + 30L * Tiles.size) else (200L)
		TimerCreateTiles.postDelayed(TimerCreateTilesRunnable, delay)
	}

	fun TimerCreateTilesTimer()
	{
		CreateTiles()
		AnimatePrepareBeforePlace()
		AnimatePlaceTilesFast()
	}

	fun CreateTiles()
	{
		for (i in Tiles.indices)
		if (Tiles[i] != null)
		{
//				TGradientAnimation *GradientAni = (TGradientAnimation*)Tiles[i].property("GradientAni").value<void *>() ;
			(Tiles[i]!!.parent as ViewGroup).removeView(Tiles[i])
			Tiles[i] = null
		}
		Tiles = arrayOfNulls(Base * Base)
		for (i in 0 until Tiles.size - 1)
		if (Tiles[i] == null)
		{
			val NewTile: Button
			NewTile = Button(this)

//				NewTile.setOnClickListener(TileClickListener);
			NewTile.setOnTouchListener(TileTouchListener)

			NewTile.text = (i + 1).toString()

			val colorAnimation = ValueAnimator()
			colorAnimation.addUpdateListener { animator ->
				NewTile.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
			}

			NewTile.tag = colorAnimation

//				NewTile.setStyleSheet(GenerateTileStyleSheet(TileFillNormalColor1, TileFillNormalColor2));
//				GradientAni.SetCurColors(TileFillNormalColor1, TileFillNormalColor2);
			NewTile.backgroundTintList = ColorStateList.valueOf(TileFillNormalColor1)
			NewTile.layoutParams = ViewGroup.LayoutParams(100, 100)
			PanelClient?.addView(NewTile)

//			NewTile.SendToBack;
			Tiles[i] = NewTile
		}

		if (Tiles[Tiles.size - 1] != null)
			Tiles[Tiles.size - 1] = null
	}

	fun ind(Row: Int, Col: Int): Int {
		return Row * Base + Col
	}


	fun ActualPosition(ATile: Button?): Int {
		for (i in Tiles.indices)
			if (Tiles[i] === ATile) return i
		return 0
	}


	fun OnTilePressed(sender: View)  = GlobalScope.async()
	{
		val SenderTile = sender as Button
		if (Mode == TMode.JustShuffled)
			SetMode(TMode.Game)
		val WasMoved: Boolean =
			TryMoveTile(ActualPosition(SenderTile), MaxMoveAniDuration,false).await()
		if (WasMoved)
			CheckPuzzleMatched()
	}


	fun TryMoveTile(TilePosition: Int, MoveAniDuration: Float, WaitAnimationEnd: Boolean): Deferred<Boolean> = GlobalScope.async()
	{
		fun MoveTile(OldPosition: Int, NewPosition: Int) = GlobalScope.async()
		{
			val temp = Tiles[NewPosition]
			Tiles[NewPosition] = Tiles[OldPosition]
			Tiles[OldPosition] = temp

			val TileNewPos = Tiles[NewPosition]
			if (TileNewPos != null)
				AnimateMoveTile(TileNewPos, MoveAniDuration, WaitAnimationEnd).await()
		}

		var WasMoved = false
		val ColPressed = TilePosition % Base
		val RowPressed = TilePosition / Base
		for (Row in 0 until Base)
			if (Tiles[ind(Row, ColPressed)] == null)
			{
				if (Row > RowPressed) //Move tiles down
					for (RowToMove in Row - 1 downTo RowPressed)
					{
						MoveTile(ind(RowToMove, ColPressed), ind(RowToMove + 1, ColPressed)).await()
						WasMoved = true
					}
				if (RowPressed > Row) //Move tiles up
					for (RowToMove in Row + 1..RowPressed)
					{
						MoveTile(ind(RowToMove, ColPressed), ind(RowToMove - 1, ColPressed)).await()
						WasMoved = true
					}
			}
		if (!WasMoved)
			for (Col in 0 until Base)
			if (Tiles[ind(RowPressed, Col)] == null)
			{
				if (Col > ColPressed) //Move tiles right
					for (ColToMove in Col - 1 downTo ColPressed)
					{
						MoveTile(ind(RowPressed, ColToMove), ind(RowPressed, ColToMove + 1)).await()
						WasMoved = true
					}
				if (ColPressed > Col) //Move tiles left
					for (ColToMove in Col + 1..ColPressed)
					{
						MoveTile(ind(RowPressed, ColToMove), ind(RowPressed, ColToMove - 1)).await()
						WasMoved = true
					}
			}

		WasMoved


	}



	fun AnimateMoveTile(ATile: Button, MoveAniDuration: Float, WaitAnimationEnd: Boolean ) = GlobalScope.async()
	{
		val ActPos = ActualPosition(ATile)
		val NewCol = ActPos % Base
		val NewRow = ActPos / Base
		val OffsetOnScaledTile = (TileSize - ATile.layoutParams.width) / 2.0f
		val X = SpaceX + Math.round(NewCol * (TileSize + TileSpacing) + OffsetOnScaledTile)
		val Y = SpaceY + Math.round(NewRow * (TileSize + TileSpacing) + OffsetOnScaledTile)
		if (MoveAniDuration > 0)
		{
//			AnimatePropertyDelay(ATile, "geometry", geometry, MoveAniDuration, 0, QEasingCurve.OutExpo, true, WaitAnimationEnd);
			ATile.animate().translationX(X.toFloat()).translationY(Y.toFloat())
				.setDuration(MoveAniDuration.toLong()).setStartDelay(0).setInterpolator(outExpo)
		} else
		{
			ATile.translationX = X.toFloat()
			ATile.translationY = Y.toFloat()
		}

		if (WaitAnimationEnd && (MoveAniDuration > 0))
			Thread.sleep(MoveAniDuration.toLong() + 50L)
	}


	fun CheckPuzzleMatched()
	{
		var LPuzzleMatched = true
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val TextNumber = Tile.text.toString().toInt()
				if (TextNumber - 1 != ActualPosition(Tiles[i]))
				{
					LPuzzleMatched = false
					break
				}
			}

		if (LPuzzleMatched && Mode == TMode.Game)
		{
			SetMode(TMode.PuzzleMatched)
			AnimatePuzzleMatched()
		}

		if (!LPuzzleMatched && (Mode == TMode.PuzzleMatched || Mode == TMode.JustShuffled))
		{
			AnimateNormalizeTilesColor()
			if (Mode == TMode.PuzzleMatched)
				SetMode(TMode.GameOver)
		}
	}


	fun ButtonShuffleOnClick(sender: View?)
	{
		AnimateNormalizeTilesColor()

		GlobalScope.async()
		{
			var NewI = 0
			val MoveCount = Tiles.size * Tiles.size
			var MoveAniDuration = MaxMoveAniDuration

			for (i in 1..MoveCount)
			{
				if (i <= 10)
					MoveAniDuration = MinMoveAniDuration + MaxMoveAniDuration * (1 - i / 10.0f)
				if (i >= MoveCount - 10)
					MoveAniDuration = MinMoveAniDuration + MaxMoveAniDuration / 2 * (1 - (MoveCount - i) / 10.0f)
				if (i > 20 && i < MoveCount - 20)
					MoveAniDuration =	if (i % 10 == 0) MinMoveAniDuration else 0f

				var WasMoved: Boolean
				do {
					NewI = RandomGen.nextInt(Tiles.size)
					WasMoved = TryMoveTile(NewI, MoveAniDuration, true).await()
				} while (!WasMoved)
			}
		}

		SetMaxTime()
		//  StopBlinkShuffle();
		SetMode(TMode.JustShuffled)
		CheckPuzzleMatched()
	}

	fun TimerTimeTimer()
	{
		Log.d("Timer", "TimerTimeTimer")
		TimeRemaining = TimeRemaining - 1
		val Sec = TimeRemaining % 60
		val Min = TimeRemaining / 60
		TextTime.text = String.format("%1\$d:%2$02d", Min, Sec)
		if (TimeRemaining == 0)
		{
			SetMode(TMode.GameOver)
			AnimateTimeOver()
			//		StartBlinkShuffle();
			return
		}

		if (TimeRemaining <= 10)
			AnimateTimeRunningOut()

		if (Mode == TMode.Game)
		{
			TimerTime.postDelayed(TimerTimeRunnable, 1000)
			Log.d("Timer",
				"TimerTime.postDelayed(TimerTimeRunnable, 1000) in TimerTimeTimer"	)
		}
	}

	fun SetMaxTime()
	{
		TimeRemaining = Base * Base * Base * Base / 20 * 10
		val Sec = TimeRemaining % 60
		val Min = TimeRemaining / 60
		TextTime.text = String.format("%1\$d:%2$02d", Min, Sec)
	}


	fun PanelClientResize()
	{
		TimerResize.removeCallbacks(TimerResizeRunnable)
		TimerResize.postDelayed(TimerResizeRunnable, 200)
	}


	fun TimerResizeTimer()
	{
		TimerResize.removeCallbacks(TimerResizeRunnable)
		val TimeFromLastResize_ms = System.currentTimeMillis() - LastResizeTime
		if (TimeFromLastResize_ms > 1000)
		{
			AnimatePlaceTilesFast()
			LastResizeTime = System.currentTimeMillis()
		}
	}

	override fun onBackPressed()
	{
		if (!ClosingAnimation)
		{
			ClosingAnimation = true
			AnimateTilesDisappeare()
			return
		}
		finish()
	}

//-------------------------------   Animations   -----------------------------

	fun CalcConsts()
	{
		val Height = PanelClient.measuredHeight
		val Width = PanelClient.measuredWidth
		if (Height > Width)
		{
			SpaceX = (Width / 20f).roundToLong()
			TileSize = ((Width - SpaceX * 2f) / Base).roundToLong()
			SpaceY = SpaceX + ((Height - Width).toFloat() / 2f).roundToLong()
		} else
		{
			SpaceY = (Height / 20f).roundToLong()
			TileSize = ((Height - SpaceY * 2f) / Base).roundToLong()
			SpaceX = SpaceY + ((Width - Height) / 2f).roundToLong()
		}
		TileSpacing = (TileSize * 0.06).roundToLong()
		TileSize = (TileSize * 0.94).roundToLong()
		SpaceX = SpaceX + (TileSpacing / 2f).roundToLong()
		SpaceY = SpaceY + (TileSpacing / 2f).roundToLong()
	}

	fun AnimatePlaceTilesFast()
	{
		CalcConsts()
		Log.d("Animate", "PlaceTilesFast")

		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				val Col = i % Base
				val Row = i / Base
				val width = Tile.layoutParams.width
				val height = Tile.layoutParams.height
				Log.d("Animate", String.format("width=%d, height=%d", width, height))
				val ScaleX = TileSize.toFloat() / width
				val ScaleY = TileSize.toFloat() / height
				val OffsetOnScaledTile = (TileSize - width) / 2.0f
				val X = SpaceX + Math.round(Col * (width * ScaleX + TileSpacing) + OffsetOnScaledTile)
				val Y = SpaceY + Math.round(Row * (height * ScaleY + TileSpacing) + OffsetOnScaledTile)

//				Tile.animate().scaleX(ScaleX).scaleY(ScaleY)
//						.translationX(X).translationY(Y)
//						.setDuration(100).setStartDelay(100l + delay).setInterpolator(linear);
				Log.d("Animate", String.format("X=%d, Y=%d, ScaleX=%g, ScaleY=%g, ", X, Y, ScaleX, ScaleY))
				AnimateFloatDelay(Tile, "scaleX", ScaleX, 200, 200 + delay)
				AnimateFloatDelay(Tile, "scaleY", ScaleY, 200, 100 + delay)
				AnimateFloatDelay(Tile, "translationX", X.toFloat(), 200, delay)
				AnimateFloatDelay(Tile, "translationY", Y.toFloat(), 100, delay)
			}
	}

	fun AnimateTilesDisappeare()
	{
		var LastTile: Button? = null
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				val X = Math.round(Tile.translationX + TileSize / 2.0)
				val Y = Math.round(Tile.translationY + TileSize).toLong()
				Tile.animate().scaleX(0.1f).scaleY(0.1f)
					.rotation(45.0f).alpha(0f)
					.translationX(X.toFloat()).translationY(Y.toFloat())
					.setDuration(400).setStartDelay(delay).setInterpolator(inBack)

//		   	AnimateFloatDelay(Tile, "scaleX", 0.1f, 400, delay);
//		   	AnimateFloatDelay(Tile, "scaleY", 0.1f, 400, delay);
//		   	AnimateFloatDelay(Tile, "rotation", 45, 400, delay);
//		   	AnimateFloatDelay(Tile, "translationY", Y, 400, delay, inBack);
//		   	AnimateFloatDelay(Tile, "translationX", X, 400, delay);
//		   	AnimateFloatDelay(Tile, "alpha", 0, 400, 100 + delay);
				LastTile = Tile
			}

//		Log.d("ClosingAnimation", " = " + ((Boolean)ClosingAnimation).toString() +
//				" LastTile =" + ((LastTile == null)? "null": LastTile.toString())
//		);
		if (ClosingAnimation && LastTile != null)
		{
			LastTile.animate().setListener(object : AnimatorListener {
				override fun onAnimationEnd(animation: Animator) {
					finish()
				}

				override fun onAnimationStart(animation: Animator) {}
				override fun onAnimationCancel(animation: Animator) {}
				override fun onAnimationRepeat(animation: Animator) {}
			})
		}
	}

	fun AnimatePrepareBeforePlace()
	{
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val ScaleX = TileSize.toFloat() / Tile.layoutParams.width
				val ScaleY = TileSize.toFloat() / Tile.layoutParams.height
				val Col = i % Base
				val Row = i / Base
				val X = SpaceX + Math.round(Col * (Tile.layoutParams.width * ScaleX + TileSpacing))
				val Y = SpaceY + Math.round(Row * (Tile.layoutParams.height * ScaleY + TileSpacing))
				Tile.scaleX = 0.5f
				Tile.scaleY = 0.5f
				Tile.alpha = 0f
				Tile.rotation = 45.0f
				Tile.translationX = X + Math.round(TileSize / 2.0).toFloat()
				Tile.translationY = Y + TileSize.toFloat()
			}

		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile

//				Tile.animate().rotation(0).alpha(1)
//						.setDuration(200).setStartDelay(delay).setInterpolator(linear);
				AnimateFloatDelay(Tile, "rotation", 0f, 400, delay)
				AnimateFloatDelay(Tile, "alpha", 1f, 400, 100 + delay)
			}
	}

	fun AnimateBaseNotChanged() {
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				val OrigScaleX = Tile.scaleX
				val OrigScaleY = Tile.scaleY
				AnimateFloatDelay(Tile, "scaleX", OrigScaleX / 2.0f, 300, delay, inBack)
				AnimateFloatDelay(Tile, "scaleY", OrigScaleY / 2.0f, 300, delay, inBack)
				AnimateFloatDelay(Tile, "scaleX", OrigScaleX, 300, 350 + delay, outBack)
				AnimateFloatDelay(Tile, "scaleY", OrigScaleY, 300, 350 + delay, outBack)
			}
	}


	fun AnimatePuzzleMatched() {
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				AnimateFloatDelay(Tile, "rotation", 360f, 1000, 350, outBack)

//				Tile.setBackgroundTintList(ColorStateList.valueOf(/*lawngreen*/0xFF7CFC00));
				val colorAnimation = Tile.tag as ValueAnimator
				val colorFrom = Tile.backgroundTintList!!.defaultColor
				val colorTo =  /*lawngreen*/0xFF7CFC00.toInt()
				colorAnimation.setObjectValues(colorFrom, colorTo)
				colorAnimation.setEvaluator(ArgbEvaluator())
				colorAnimation.duration = 1000
				colorAnimation.startDelay = delay
				colorAnimation.repeatCount = 0
				colorAnimation.start()
			}
	}

	fun AnimateTimeRunningOut() {
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
//				Tile.setBackgroundTintList(ColorStateList.valueOf(/*darkorange*/0xFFFF8C00));
				val colorAnimation = Tile.tag as ValueAnimator
				val colorFrom = Tile.backgroundTintList!!.defaultColor
				val colorTo =  /*darkorange*/0xFFFF8C00.toInt()
				colorAnimation.setObjectValues(colorFrom, colorTo)
				colorAnimation.setEvaluator(ArgbEvaluator())
				colorAnimation.duration = 150
				colorAnimation.startDelay = 0
				colorAnimation.repeatCount = 1
				colorAnimation.repeatMode = ObjectAnimator.REVERSE
				colorAnimation.start()
			}
	}

	fun AnimateTimeOver() {
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				//				Tile.setBackgroundTintList(ColorStateList.valueOf(/*red*/0xFFFF0000));
				val colorAnimation = Tile.tag as ValueAnimator
				val colorFrom = Tile.backgroundTintList!!.defaultColor
				val colorTo =  /*red*/0xFFFF0000.toInt()
				colorAnimation.setObjectValues(colorFrom, colorTo)
				colorAnimation.setEvaluator(ArgbEvaluator())
				colorAnimation.duration = 1000
				colorAnimation.startDelay = delay
				colorAnimation.repeatCount = 0
				colorAnimation.start()
			}
	}

	fun AnimateNormalizeTilesColor() {
		for ((i, Tile) in Tiles.withIndex())
			if (Tile != null)
			{
				val delay = 30L * i //delay for tile
				//				Tile.setBackgroundTintList(ColorStateList.valueOf(TileFillNormalColor1));
				val colorAnimation = Tile.tag as ValueAnimator
				val colorFrom = Tile.backgroundTintList!!.defaultColor
				val colorTo = TileFillNormalColor1
				colorAnimation.setObjectValues(colorFrom, colorTo)
				colorAnimation.setEvaluator(ArgbEvaluator())
				colorAnimation.duration = 1000
				colorAnimation.startDelay = delay
				colorAnimation.repeatCount = 0
				colorAnimation.start()
			}
	}


//-------------------------------  Test different Animations   -----------------------------
	fun ButtonDisappeareOnClick(sender: View?) {
		AnimateTilesDisappeare()
	}

	fun ButtonPlaceOnClick(sender: View?) {
		AnimateNormalizeTilesColor()
		AnimatePrepareBeforePlace()
		AnimatePlaceTilesFast()
	}

	fun ButtonTimeOverOnClick(sender: View?) {
		AnimateTimeOver()
	}

	fun ButtonTimeRunningOutOnClick(sender: View?) {
		AnimateTimeRunningOut()
	}

	fun ButtonPuzzleMatchedOnClick(sender: View?) {
		AnimatePuzzleMatched()
	}


//---------------------------  Realization of Property Animation   -----------------------------


	fun AnimateFloatDelay(
		Target: View, PropertyName: String,
		Value: Float, Duration_ms: Long, Delay_ms: Long,
		AInterpolator: TimeInterpolator = linear
		/*, boolean DeleteWhenStopped, boolean WaitAnimationEnd*/	): ObjectAnimator
	{
		val objectAnimator = ObjectAnimator.ofFloat(Target, PropertyName, Value)
		objectAnimator.duration = Duration_ms
		objectAnimator.startDelay = Delay_ms
		//		objectAnimator.setRepeatCount(1);
//		objectAnimator.setRepeatMode(ObjectAnimator.REVERSE);
		objectAnimator.interpolator = AInterpolator
		objectAnimator.start()
		return objectAnimator
	}

}
