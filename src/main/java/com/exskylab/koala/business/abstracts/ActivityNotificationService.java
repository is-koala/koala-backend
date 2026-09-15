package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.Review;
import com.exskylab.koala.entities.User;

public interface ActivityNotificationService {

    void applicationAnswered(JobApplication application);

    void applicationWithdrawn(JobApplication application);

    void assignmentOffered(JobAssignment assignment);

    void assignmentAnswered(JobAssignment assignment);

    void assignmentCompleted(JobAssignment assignment);

    void payoutReleased(JobAssignment assignment);

    void disputeOpened(Dispute dispute);

    void disputeResolved(Dispute dispute);

    void assignmentCancelled(Cancellation cancellation);

    void assignmentExpired(Cancellation cancellation);

    void reviewReceived(Review review);

    void identityVerified(User user);

    void identityRevealed(JobAssignment assignment);

    void companyApprovalDecided(Company company);

    void messageSent(Message message);
}
