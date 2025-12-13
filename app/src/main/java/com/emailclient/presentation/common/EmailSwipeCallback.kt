package com.emailclient.presentation.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.R
import com.emailclient.domain.model.SwipeAction

/**
 * ItemTouchHelper callback for handling swipe gestures on email list items
 */
class EmailSwipeCallback(
    private val context: Context,
    private val onSwipeLeft: (Int) -> Unit,
    private val onSwipeRight: (Int) -> Unit,
    private val getSwipeLeftAction: () -> SwipeAction,
    private val getSwipeRightAction: () -> SwipeAction
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val paint = Paint()
    private val iconMargin = context.resources.getDimensionPixelSize(R.dimen.swipe_icon_margin)
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.swipe_icon_size)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Not supporting drag and drop
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return

        when (direction) {
            ItemTouchHelper.LEFT -> onSwipeLeft(position)
            ItemTouchHelper.RIGHT -> onSwipeRight(position)
        }
    }

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val leftAction = getSwipeLeftAction()
        val rightAction = getSwipeRightAction()

        // Disable swipe if both actions are NONE
        return when {
            leftAction == SwipeAction.NONE && rightAction == SwipeAction.NONE -> 0
            leftAction == SwipeAction.NONE -> ItemTouchHelper.RIGHT
            rightAction == SwipeAction.NONE -> ItemTouchHelper.LEFT
            else -> ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        if (dX > 0) {
            // Swiping right
            drawSwipeBackground(c, itemView, dX, getSwipeRightAction(), isRight = true)
        } else if (dX < 0) {
            // Swiping left
            drawSwipeBackground(c, itemView, dX, getSwipeLeftAction(), isRight = false)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawSwipeBackground(
        canvas: Canvas,
        itemView: View,
        dX: Float,
        action: SwipeAction,
        isRight: Boolean
    ) {
        if (action == SwipeAction.NONE) return

        // Determine background color and icon based on action
        val (backgroundColor, iconRes) = when (action) {
            SwipeAction.ARCHIVE -> Pair(
                ContextCompat.getColor(context, R.color.swipe_archive_background),
                R.drawable.ic_archive
            )
            SwipeAction.DELETE -> Pair(
                ContextCompat.getColor(context, R.color.swipe_delete_background),
                R.drawable.ic_delete
            )
            SwipeAction.MARK_READ -> Pair(
                ContextCompat.getColor(context, R.color.swipe_read_background),
                R.drawable.ic_mark_read
            )
            SwipeAction.NONE -> return
        }

        // Draw background
        paint.color = backgroundColor
        if (isRight) {
            // Swipe right - background on left side
            canvas.drawRect(
                itemView.left.toFloat(),
                itemView.top.toFloat(),
                itemView.left + dX,
                itemView.bottom.toFloat(),
                paint
            )
        } else {
            // Swipe left - background on right side
            canvas.drawRect(
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat(),
                paint
            )
        }

        // Draw icon
        val icon = ContextCompat.getDrawable(context, iconRes) ?: return
        val verticalCenter = itemView.top + (itemView.height - iconSize) / 2

        if (isRight) {
            // Icon on left side
            val left = itemView.left + iconMargin
            icon.setBounds(left, verticalCenter, left + iconSize, verticalCenter + iconSize)
        } else {
            // Icon on right side
            val right = itemView.right - iconMargin
            icon.setBounds(right - iconSize, verticalCenter, right, verticalCenter + iconSize)
        }

        icon.setTint(Color.WHITE)
        icon.draw(canvas)
    }
}
