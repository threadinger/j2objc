// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//
//  IOSConcreteClass.h
//  JreEmulation
//
//  Created by Keith Stanger on 8/16/13.
//

#ifndef _IOSConcreteClass_H_
#define _IOSConcreteClass_H_

#import "IOSClass.h"
#import "IOSMetadata.h"

@interface IOSConcreteClass : IOSClass {
  Class class_;
}

- (instancetype)initWithClass:(Class)cls
                     metadata:(const J2ObjcClassInfo *)metadata
                                name:(NSString *)clsName
                       simpleNamePos:(int)simpleNamePos;

@end

#endif // _IOSConcreteClass_H_
