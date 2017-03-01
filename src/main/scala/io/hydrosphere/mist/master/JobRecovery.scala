package io.hydrosphere.mist.master

import akka.actor.{Actor, Props}
import io.hydrosphere.mist.MistConfig
import io.hydrosphere.mist.jobs.JobDetails
import io.hydrosphere.mist.jobs.store.JobRepository
import io.hydrosphere.mist.master.JobRecovery.StartRecovery
import io.hydrosphere.mist.master.async.AsyncInterface
import io.hydrosphere.mist.utils.Logger

private[mist] object JobRecovery {
  
  sealed trait Message
  case object StartRecovery extends Message
  
  def props(): Props = Props(classOf[JobRecovery])
  
}

private[mist] class JobRecovery extends Actor with Logger {

  override def receive: Receive = {
    case StartRecovery =>
      JobRepository()
        .filteredByStatuses(List(JobDetails.Status.Running, JobDetails.Status.Queued, JobDetails.Status.Initialized))
        .groupBy(_.source)
        .foreach({
          case (source: JobDetails.Source, jobs: List[JobDetails]) => source match {
            case s: JobDetails.Source.Async => 
              logger.info(s"${jobs.length} jobs must be sent to ${s.provider}")
              if (MistConfig.AsyncInterfaceConfig(s.provider).isOn) {
                jobs.foreach {
                  job => AsyncInterface.subscriber(s.provider, Some(context)) ! job
                }
              }
            case _ =>
              logger.debug(s"${jobs.length} jobs must be marked as aborted")
              jobs.foreach {
                job => JobRepository().update(job.withStatus(JobDetails.Status.Aborted))
              }
          }
        })
  }
  
}
