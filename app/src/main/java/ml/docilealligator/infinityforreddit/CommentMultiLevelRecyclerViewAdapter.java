package ml.docilealligator.infinityforreddit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.multilevelview.MultiLevelAdapter;
import com.multilevelview.MultiLevelRecyclerView;
import com.multilevelview.models.RecyclerViewItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Retrofit;
import ru.noties.markwon.view.MarkwonView;

class CommentMultiLevelRecyclerViewAdapter extends MultiLevelAdapter {
    private Context mContext;
    private Retrofit mRetrofit;
    private Retrofit mOauthRetrofit;
    private SharedPreferences mSharedPreferences;
    private ArrayList<CommentData> mCommentData;
    private MultiLevelRecyclerView mMultiLevelRecyclerView;
    private String subredditNamePrefixed;
    private String article;
    private Locale locale;

    CommentMultiLevelRecyclerViewAdapter(Context context, Retrofit retrofit, Retrofit oauthRetrofit,
                                         SharedPreferences sharedPreferences, ArrayList<CommentData> commentData,
                                         MultiLevelRecyclerView multiLevelRecyclerView,
                                         String subredditNamePrefixed, String article, Locale locale) {
        super(commentData);
        mContext = context;
        mRetrofit = retrofit;
        mOauthRetrofit = oauthRetrofit;
        mSharedPreferences = sharedPreferences;
        mCommentData = commentData;
        mMultiLevelRecyclerView = multiLevelRecyclerView;
        this.subredditNamePrefixed = subredditNamePrefixed;
        this.article = article;
        this.locale = locale;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CommentViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_comment, parent, false));
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final CommentData commentItem = mCommentData.get(position);

        ((CommentViewHolder) holder).authorTextView.setText(commentItem.getAuthor());
        ((CommentViewHolder) holder).commentTimeTextView.setText(commentItem.getCommentTime());
        ((CommentViewHolder) holder).commentMarkdownView.setMarkdown(commentItem.getCommentContent());
        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore()));

        ((CommentViewHolder) holder).verticalBlock.getLayoutParams().width = commentItem.getDepth() * 16;
        if(commentItem.hasReply()) {
            setExpandButton(((CommentViewHolder) holder).expandButton, commentItem.isExpanded());
        }

        ((CommentViewHolder) holder).expandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(commentItem.hasChildren() && commentItem.getChildren().size() > 0) {
                    mMultiLevelRecyclerView.toggleItemsGroup(holder.getAdapterPosition());
                    setExpandButton(((CommentViewHolder) holder).expandButton, commentItem.isExpanded());
                } else {
                    ((CommentViewHolder) holder).loadMoreCommentsProgressBar.setVisibility(View.VISIBLE);
                    FetchComment.fetchComment(mRetrofit, subredditNamePrefixed, article, commentItem.getId(),
                            new FetchComment.FetchCommentListener() {
                                @Override
                                public void onFetchCommentSuccess(String response) {
                                    ParseComment.parseComment(response, new ArrayList<CommentData>(),
                                            locale, false, commentItem.getDepth(), new ParseComment.ParseCommentListener() {
                                                @Override
                                                public void onParseCommentSuccess(List<?> commentData, int moreCommentCount) {
                                                    commentItem.addChildren((List<RecyclerViewItem>) commentData);
                                                    ((CommentViewHolder) holder).loadMoreCommentsProgressBar
                                                            .setVisibility(View.GONE);
                                                    mMultiLevelRecyclerView.toggleItemsGroup(holder.getAdapterPosition());
                                                    ((CommentViewHolder) holder).expandButton
                                                            .setImageResource(R.drawable.ic_expand_less_black_20dp);
                                                }

                                                @Override
                                                public void onParseCommentFail() {
                                                    ((CommentViewHolder) holder).loadMoreCommentsProgressBar
                                                            .setVisibility(View.GONE);
                                                }
                                            });
                                }

                                @Override
                                public void onFetchCommentFail() {

                                }
                            });
                }
            }
        });

        switch (commentItem.getVoteType()) {
            case 1:
                ((CommentViewHolder) holder).upvoteButton
                        .setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary), android.graphics.PorterDuff.Mode.SRC_IN);
                break;
            case 2:
                ((CommentViewHolder) holder).downvoteButton
                        .setColorFilter(ContextCompat.getColor(mContext, R.color.minusButtonColor), android.graphics.PorterDuff.Mode.SRC_IN);
                break;
        }

        ((CommentViewHolder) holder).upvoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean isDownvotedBefore = ((CommentViewHolder) holder).downvoteButton.getColorFilter() != null;
                final ColorFilter minusButtonColorFilter = ((CommentViewHolder) holder).downvoteButton.getColorFilter();
                ((CommentViewHolder) holder).downvoteButton.clearColorFilter();

                if (((CommentViewHolder) holder).upvoteButton.getColorFilter() == null) {
                    ((CommentViewHolder) holder).upvoteButton.setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary), android.graphics.PorterDuff.Mode.SRC_IN);
                    if(isDownvotedBefore) {
                        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + 2));
                    } else {
                        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + 1));
                    }

                    VoteThing.voteThing(mOauthRetrofit,mSharedPreferences,  new VoteThing.VoteThingListener() {
                        @Override
                        public void onVoteThingSuccess(int position) {
                            commentItem.setVoteType(1);
                            if(isDownvotedBefore) {
                                commentItem.setScore(commentItem.getScore() + 2);
                            } else {
                                commentItem.setScore(commentItem.getScore() + 1);
                            }
                        }

                        @Override
                        public void onVoteThingFail(int position) {
                            Toast.makeText(mContext, "Cannot upvote this comment", Toast.LENGTH_SHORT).show();
                            ((CommentViewHolder) holder).upvoteButton.clearColorFilter();
                            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore()));
                            ((CommentViewHolder) holder).downvoteButton.setColorFilter(minusButtonColorFilter);
                        }
                    }, commentItem.getFullName(), RedditUtils.DIR_UPVOTE, ((CommentViewHolder) holder).getAdapterPosition());
                } else {
                    //Upvoted before
                    ((CommentViewHolder) holder).upvoteButton.clearColorFilter();
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() - 1));

                    VoteThing.voteThing(mOauthRetrofit, mSharedPreferences, new VoteThing.VoteThingListener() {
                        @Override
                        public void onVoteThingSuccess(int position) {
                            commentItem.setVoteType(0);
                            commentItem.setScore(commentItem.getScore() - 1);
                        }

                        @Override
                        public void onVoteThingFail(int position) {
                            Toast.makeText(mContext, "Cannot unvote this comment", Toast.LENGTH_SHORT).show();
                            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + 1));
                            ((CommentViewHolder) holder).upvoteButton.setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary), android.graphics.PorterDuff.Mode.SRC_IN);
                            commentItem.setScore(commentItem.getScore() + 1);
                        }
                    }, commentItem.getFullName(), RedditUtils.DIR_UNVOTE, ((CommentViewHolder) holder).getAdapterPosition());
                }
            }
        });

        ((CommentViewHolder) holder).downvoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean isUpvotedBefore = ((CommentViewHolder) holder).upvoteButton.getColorFilter() != null;

                final ColorFilter upvoteButtonColorFilter = ((CommentViewHolder) holder).upvoteButton.getColorFilter();
                ((CommentViewHolder) holder).upvoteButton.clearColorFilter();

                if (((CommentViewHolder) holder).downvoteButton.getColorFilter() == null) {
                    ((CommentViewHolder) holder).downvoteButton.setColorFilter(ContextCompat.getColor(mContext, R.color.minusButtonColor), android.graphics.PorterDuff.Mode.SRC_IN);
                    if (isUpvotedBefore) {
                        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() - 2));
                    } else {
                        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() - 1));
                    }

                    VoteThing.voteThing(mOauthRetrofit, mSharedPreferences, new VoteThing.VoteThingListener() {
                        @Override
                        public void onVoteThingSuccess(int position) {
                            commentItem.setVoteType(-1);
                            if(isUpvotedBefore) {
                                commentItem.setScore(commentItem.getScore() - 2);
                            } else {
                                commentItem.setScore(commentItem.getScore() - 1);
                            }
                        }

                        @Override
                        public void onVoteThingFail(int position) {
                            Toast.makeText(mContext, "Cannot downvote this comment", Toast.LENGTH_SHORT).show();
                            ((CommentViewHolder) holder).downvoteButton.clearColorFilter();
                            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore()));
                            ((CommentViewHolder) holder).upvoteButton.setColorFilter(upvoteButtonColorFilter);
                        }
                    }, commentItem.getFullName(), RedditUtils.DIR_DOWNVOTE, holder.getAdapterPosition());
                } else {
                    //Down voted before
                    ((CommentViewHolder) holder).downvoteButton.clearColorFilter();
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + 1));

                    VoteThing.voteThing(mOauthRetrofit, mSharedPreferences, new VoteThing.VoteThingListener() {
                        @Override
                        public void onVoteThingSuccess(int position) {
                            commentItem.setVoteType(0);
                            commentItem.setScore(commentItem.getScore());
                        }

                        @Override
                        public void onVoteThingFail(int position) {
                            Toast.makeText(mContext, "Cannot unvote this comment", Toast.LENGTH_SHORT).show();
                            ((CommentViewHolder) holder).downvoteButton.setColorFilter(ContextCompat.getColor(mContext, R.color.minusButtonColor), android.graphics.PorterDuff.Mode.SRC_IN);
                            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore()));
                            commentItem.setScore(commentItem.getScore());
                        }
                    }, commentItem.getFullName(), RedditUtils.DIR_UNVOTE, holder.getAdapterPosition());
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        ((CommentViewHolder) holder).expandButton.setVisibility(View.GONE);
        ((CommentViewHolder) holder).loadMoreCommentsProgressBar.setVisibility(View.GONE);
    }

    private void setExpandButton(ImageView expandButton, boolean isExpanded) {
        // set the icon based on the current state
        expandButton.setVisibility(View.VISIBLE);
        expandButton.setImageResource(isExpanded ? R.drawable.ic_expand_less_black_20dp : R.drawable.ic_expand_more_black_20dp);
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.author_text_view_item_post_comment) TextView authorTextView;
        @BindView(R.id.comment_time_text_view_item_post_comment) TextView commentTimeTextView;
        @BindView(R.id.comment_markdown_view_item_post_comment) MarkwonView commentMarkdownView;
        @BindView(R.id.plus_button_item_post_comment) ImageView upvoteButton;
        @BindView(R.id.score_text_view_item_post_comment) TextView scoreTextView;
        @BindView(R.id.minus_button_item_post_comment) ImageView downvoteButton;
        @BindView(R.id.expand_button_item_post_comment) ImageView expandButton;
        @BindView(R.id.load_more_comments_progress_bar) ProgressBar loadMoreCommentsProgressBar;
        @BindView(R.id.reply_button_item_post_comment) ImageView replyButton;
        @BindView(R.id.vertical_block_item_post_comment) View verticalBlock;

        CommentViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
